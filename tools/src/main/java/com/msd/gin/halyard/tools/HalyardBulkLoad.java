/*
 * Copyright 2016 Merck Sharp & Dohme Corp. a subsidiary of Merck & Co.,
 * Inc., Kenilworth, NJ, USA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.msd.gin.halyard.tools;

import com.msd.gin.halyard.common.HalyardTableUtils;
import com.msd.gin.halyard.common.IdValueFactory;
import com.msd.gin.halyard.common.RDFFactory;
import com.msd.gin.halyard.common.StatementIndices;
import com.msd.gin.halyard.rio.TriGStarParser;
import com.msd.gin.halyard.util.LFUCache;
import com.msd.gin.halyard.util.LRUCache;
import com.msd.gin.halyard.vocab.HALYARD;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.cli.CommandLine;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.Seekable;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.tool.BulkLoadHFiles;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.compress.CodecPool;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.CombineFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.CombineFileSplit;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.ParseErrorListener;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFParserFactory;
import org.eclipse.rdf4j.rio.RDFParserRegistry;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.eclipse.rdf4j.rio.helpers.NTriplesParserSettings;
import org.eclipse.rdf4j.rio.helpers.NTriplesUtil;
import org.eclipse.rdf4j.rio.trix.TriXParser;
import org.eclipse.rdf4j.rio.turtle.TurtleParser;

/**
 * Apache Hadoop MapReduce Tool for bulk loading RDF into HBase
 * @author Adam Sotona (MSD)
 */
public final class HalyardBulkLoad extends AbstractHalyardTool {
	private static final String TOOL_NAME = "bulkload";

    /**
     * Property defining number of bits used for HBase region pre-splits calculation for new table
     */
    public static final String SPLIT_BITS_PROPERTY = confProperty(TOOL_NAME, "table.splitbits");

    /**
     * Property truncating existing HBase table just before the bulk load
     */
    public static final String TRUNCATE_PROPERTY = confProperty(TOOL_NAME, "table.truncate");

    /**
     * Property defining exact timestamp of all loaded triples (System.currentTimeMillis() is the default value)
     */
    public static final String TIMESTAMP_PROPERTY = confProperty(TOOL_NAME, "timestamp");

    /**
     * Boolean property ignoring RDF parsing errors
     */
    public static final String ALLOW_INVALID_IRIS_PROPERTY = confProperty("parser", "allow-invalid-iris");

    /**
     * Boolean property ignoring RDF parsing errors
     */
    public static final String SKIP_INVALID_LINES_PROPERTY = confProperty("parser", "skip-invalid-lines");

    /**
     * Boolean property enabling RDF parser verification of data values
     */
    public static final String VERIFY_DATATYPE_VALUES_PROPERTY = confProperty("parser", "verify-datatype-values");

    /**
     * Boolean property enforcing triples and quads context override with the default context
     */
    public static final String OVERRIDE_CONTEXT_PROPERTY = confProperty("parser", "context.override");

    /**
     * Property defining default context for triples (or even for quads when context override is set)
     */
    public static final String DEFAULT_CONTEXT_PROPERTY = confProperty("parser", "context.default");

    /**
     * Multiplier limiting maximum single file size in relation to the maximum split size, before it is processed in parallel (10x maximum split size)
     */
    private static final long MAX_SINGLE_FILE_MULTIPLIER = 10;
    private static final int DEFAULT_SPLIT_BITS = 3;
    private static final long DEFAULT_SPLIT_MAXSIZE = 200000000l;

    enum Counters {
		ADDED_KVS,
		ADDED_STATEMENTS,
    	TOTAL_STATEMENTS_READ
	}

    static void replaceParser(RDFFormat format, RDFParserFactory newpf) {
        RDFParserRegistry reg = RDFParserRegistry.getInstance();
        reg.get(format).ifPresent(pf -> reg.remove(pf));
        reg.add(newpf);
    }

    static void setParsers() {
        // this is a workaround to avoid autodetection of .xml files as TriX format and hook on .trix file extension only
        replaceParser(RDFFormat.TRIX, new RDFParserFactory() {
            @Override
            public RDFFormat getRDFFormat() {
                RDFFormat t = RDFFormat.TRIX;
                return new RDFFormat(t.getName(), t.getMIMETypes(), t.getCharset(), Arrays.asList("trix"), t.getStandardURI(), t.supportsNamespaces(), t.supportsContexts(), t.supportsRDFStar());
            }

            @Override
            public RDFParser getParser() {
                return new TriXParser();
            }
        });
        // this is a workaround to make Turtle parser more resistant to invalid URIs when in dirty mode
        replaceParser(RDFFormat.TURTLE, new RDFParserFactory() {
            @Override
            public RDFFormat getRDFFormat() {
                return RDFFormat.TURTLE;
            }
            @Override
            public RDFParser getParser() {
                return new TurtleParser(){
                    @Override
                    protected IRI parseURI() throws IOException, RDFParseException {
                        try {
                            return super.parseURI();
                        } catch (RuntimeException e) {
                            if (getParserConfig().get(NTriplesParserSettings.FAIL_ON_INVALID_LINES)) {
                                throw e;
                            } else {
                                reportError(e, NTriplesParserSettings.FAIL_ON_INVALID_LINES);
                                return null;
                            }
                        }
                    }
                    @Override
                    protected Literal createLiteral(String label, String lang, IRI datatype, long lineNo, long columnNo) throws RDFParseException {
                        try {
                            return super.createLiteral(label, lang, datatype, lineNo, columnNo);
                        } catch (RuntimeException e) {
                            if (getParserConfig().get(NTriplesParserSettings.FAIL_ON_INVALID_LINES)) {
                                throw e;
                            } else {
                                reportError(e, NTriplesParserSettings.FAIL_ON_INVALID_LINES);
                                return super.createLiteral(label, null, null, lineNo, columnNo);
                            }
                        }
                    }
                };
            }
        });
        // this is a workaround for https://github.com/eclipse/rdf4j/issues/3664
        replaceParser(RDFFormat.TRIGSTAR, new TriGStarParser.Factory());
    }

    /**
     * Mapper class transforming each parsed Statement into set of HBase KeyValues
     */
    public static final class RDFMapper extends Mapper<LongWritable, Statement, ImmutableBytesWritable, KeyValue> {

        private final ImmutableBytesWritable rowKey = new ImmutableBytesWritable();
        private final Set<Statement> stmtDedup = Collections.newSetFromMap(new LFUCache<>(2000, 0.1f));
        private StatementIndices stmtIndices;
        private long timestamp;
        private long addedKvs;
        private long addedStmts;
        private long totalStmtsRead;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            RDFFactory rdfFactory = RDFFactory.create(conf);
            stmtIndices = new StatementIndices(conf, rdfFactory);
            timestamp = conf.getLong(TIMESTAMP_PROPERTY, System.currentTimeMillis());
        }

        @Override
        protected void map(LongWritable key, Statement stmt, final Context output) throws IOException, InterruptedException {
        	// best effort statement deduplication
        	if (stmtDedup.add(stmt)) {
        		List<? extends KeyValue> kvs;
        		if (HALYARD.SYSTEM_GRAPH_CONTEXT.equals(stmt.getContext())) {
        			kvs = HalyardTableUtils.insertSystemKeyValues(stmt.getSubject(), stmt.getPredicate(), stmt.getObject(), stmt.getContext(), timestamp, stmtIndices);
        		} else {
        			kvs = HalyardTableUtils.insertKeyValues(stmt.getSubject(), stmt.getPredicate(), stmt.getObject(), stmt.getContext(), timestamp, stmtIndices);
        		}
	            for (KeyValue keyValue: kvs) {
	                rowKey.set(keyValue.getRowArray(), keyValue.getRowOffset(), keyValue.getRowLength());
	                output.write(rowKey, keyValue);
	                addedKvs++;
	            }
	            addedStmts++;
        	}
        	totalStmtsRead++;
        }

        @Override
        protected void cleanup(Context output) throws IOException {
        	output.getCounter(Counters.ADDED_KVS).increment(addedKvs);
        	output.getCounter(Counters.ADDED_STATEMENTS).increment(addedStmts);
        	output.getCounter(Counters.TOTAL_STATEMENTS_READ).increment(totalStmtsRead);
        }
    }

    /**
     * MapReduce FileInputFormat reading and parsing any RDF4J RIO supported RDF format into Statements
     */
    public static final class RioFileInputFormat extends CombineFileInputFormat<LongWritable, Statement> {

        public RioFileInputFormat() {
            setParsers();
        }

        @Override
        protected boolean isSplitable(JobContext context, Path file) {
            return false;
        }

        @Override
        public List<InputSplit> getSplits(JobContext job) throws IOException {
            List<InputSplit> splits = super.getSplits(job);
            long maxSize = MAX_SINGLE_FILE_MULTIPLIER * job.getConfiguration().getLong(FileInputFormat.SPLIT_MAXSIZE, 0);
            if (maxSize > 0) {
                List<InputSplit> newSplits = new ArrayList<>();
                for (InputSplit spl : splits) {
                    CombineFileSplit cfs = (CombineFileSplit)spl;
                    for (int i=0; i<cfs.getNumPaths(); i++) {
                        long length = cfs.getLength();
                        if (length > maxSize) {
                            int replicas = (int)Math.ceil((double)length / (double)maxSize);
                            Path path = cfs.getPath(i);
                            for (int r=1; r<replicas; r++) {
                                newSplits.add(new CombineFileSplit(new Path[]{path}, new long[]{r}, new long[]{length}, cfs.getLocations()));
                            }
                        }
                    }
                }
                splits.addAll(newSplits);
            }
            return splits;
        }

        @Override
        protected List<FileStatus> listStatus(JobContext job) throws IOException {
            List<FileStatus> filteredList = new ArrayList<>();
            for (FileStatus fs : super.listStatus(job)) {
                if (Rio.getParserFormatForFileName(fs.getPath().getName()) != null) {
                    filteredList.add(fs);
                }
            }
            return filteredList;
        }

        @Override
        public RecordReader<LongWritable, Statement> createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException {
            return new RecordReader<LongWritable, Statement>() {

                private final AtomicLong key = new AtomicLong();

                private ParserPump pump = null;
                private LongWritable currentKey = new LongWritable();
                private Statement currentValue = null;
                private Thread pumpThread = null;

                @Override
                public void initialize(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
                    close();
                    pump = new ParserPump((CombineFileSplit)split, context);
                    pumpThread = new Thread(pump);
                    pumpThread.setDaemon(true);
                    pumpThread.start();
                }

                @Override
                public boolean nextKeyValue() throws IOException, InterruptedException {
                    if (pump != null) {
                        currentValue = pump.getNext();
                        if (currentValue != null) {
                            currentKey.set(key.incrementAndGet());
                            return true;
                        }
                    }
                    currentKey = null;
                    currentValue = null;
                    return false;
                }

                @Override
                public LongWritable getCurrentKey() throws IOException, InterruptedException {
                    return currentKey;
                }

                @Override
                public Statement getCurrentValue() throws IOException, InterruptedException {
                    return currentValue;
                }

                @Override
                public float getProgress() throws IOException, InterruptedException {
                    return pump == null ? 0 : pump.getProgress();
                }

                @Override
                public void close() throws IOException {
                    if (pump != null) {
                        pump.close();
                        pump = null;
                    }
                    if (pumpThread != null) {
                        pumpThread.interrupt();
                        pumpThread = null;
                    }
                }
            };
        }
    }

    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final IRI NOP = VF.createIRI(":");
    private static final Statement END_STATEMENT = VF.createStatement(NOP, NOP, NOP);

    private static final class ParserPump extends AbstractRDFHandler implements Closeable, Runnable, ParseErrorListener {

    	enum Counters {
    		PARSE_QUEUE_EMPTY,
    		PARSE_QUEUE_FULL,
    		LRU_CACHE_HITS,
    		LRU_CACHE_MISSES,
    		LFU_CACHE_HITS,
    		LFU_CACHE_MISSES,
    		BOTH_CACHE_HITS,
    		BOTH_CACHE_MISSES
    	}

        private final BlockingQueue<Statement> queue = new LinkedBlockingQueue<>(500);
        private final CachingIdValueFactory valueFactory = new CachingIdValueFactory();
        private final TaskAttemptContext context;
        private final Path paths[];
        private final long[] sizes, offsets;
        private final long size;
        private final boolean allowInvalidIris, skipInvalidLines, verifyDataTypeValues;
        private final String defaultRdfContextPattern;
        private final boolean overrideRdfContext;
        private final long maxSize;
        private volatile Exception ex = null;
        private long finishedSize = 0L;
        private int offset, count;

        private String baseUri = "";
        private Seekable seek;
        private InputStream in;

        public ParserPump(CombineFileSplit split, TaskAttemptContext context) {
            this.context = context;
            this.paths = split.getPaths();
            this.sizes = split.getLengths();
            this.offsets = split.getStartOffsets();
            this.size = split.getLength();
            Configuration conf = context.getConfiguration();
            this.allowInvalidIris = conf.getBoolean(ALLOW_INVALID_IRIS_PROPERTY, false);
            this.skipInvalidLines = conf.getBoolean(SKIP_INVALID_LINES_PROPERTY, false);
            this.verifyDataTypeValues = conf.getBoolean(VERIFY_DATATYPE_VALUES_PROPERTY, false);
            this.overrideRdfContext = conf.getBoolean(OVERRIDE_CONTEXT_PROPERTY, false);
            this.defaultRdfContextPattern = conf.get(DEFAULT_CONTEXT_PROPERTY);
            this.maxSize = MAX_SINGLE_FILE_MULTIPLIER * conf.getLong(FileInputFormat.SPLIT_MAXSIZE, 0);
        }

        public Statement getNext() throws IOException, InterruptedException {
            // remove from queue even on error to empty it
            Statement s = queue.poll();
            if (s == null) {
            	context.getCounter(Counters.PARSE_QUEUE_EMPTY).increment(1);
            	s = queue.take();
            }
            if (ex != null) {
                throw new IOException("Exception while parsing: " + baseUri, ex);
            }
            return s == END_STATEMENT ? null : s;
        }

        public synchronized float getProgress() {
            try {
                long seekPos = (seek != null) ? seek.getPos() : 0L;
                return (float)(finishedSize + seekPos) / (float)size;
            } catch (IOException e) {
                return (float)finishedSize / (float)size;
            }
        }

        @Override
        public void run() {
            setParsers();
            try {
                Configuration conf = context.getConfiguration();
                for (int i=0; i<paths.length; i++) try {
                    Path file = paths[i];
                    final String localBaseUri = file.toString();
                    synchronized (this) {
                        this.baseUri = localBaseUri; //synchronised parameters must be set inside a sync block
                        if (seek != null) try {
                            finishedSize += seek.getPos();
                        } catch (IOException e) {
                            //ignore
                        }
                    }
                    this.offset = (int)offsets[i];
                    this.count = (maxSize > 0 && sizes[i] > maxSize) ? (int)Math.ceil((double)sizes[i] / (double)maxSize) : 1;
                    close();
                    context.setStatus("Parsing " + localBaseUri);
                    FileSystem fs = file.getFileSystem(conf);
                    FSDataInputStream fileIn = fs.open(file);
                    CompressionCodec codec = new CompressionCodecFactory(conf).getCodec(file);
                    final InputStream localIn;
                    if (codec != null) {
                    	localIn = codec.createInputStream(fileIn, CodecPool.getDecompressor(codec));
                    } else {
                    	localIn = fileIn;
                    }
                    synchronized (this) {
                        this.seek = fileIn; //synchronised parameters must be set inside a sync block
                        this.in = localIn; //synchronised parameters must be set inside a sync block
                    }
                    RDFParser parser = Rio.createParser(Rio.getParserFormatForFileName(localBaseUri).get());
                    parser.setRDFHandler(this);
                    parser.setParseErrorListener(this);
                    parser.set(BasicParserSettings.PRESERVE_BNODE_IDS, true);
                    parser.set(BasicParserSettings.VERIFY_URI_SYNTAX, !allowInvalidIris);
                    parser.set(BasicParserSettings.VERIFY_RELATIVE_URIS, !allowInvalidIris);
                    if (skipInvalidLines) {
                        parser.set(NTriplesParserSettings.FAIL_ON_INVALID_LINES, false);
                        parser.getParserConfig().addNonFatalError(NTriplesParserSettings.FAIL_ON_INVALID_LINES);
                    }
                   	parser.set(BasicParserSettings.VERIFY_DATATYPE_VALUES, verifyDataTypeValues);
                    parser.set(BasicParserSettings.VERIFY_LANGUAGE_TAGS, verifyDataTypeValues);
                    if (defaultRdfContextPattern != null || overrideRdfContext) {
                        IRI defaultRdfContext;
                        if (defaultRdfContextPattern != null) {
                            String context = MessageFormat.format(defaultRdfContextPattern, localBaseUri, file.toUri().getPath(), file.getName());
                            validateIRIs(context);
                            defaultRdfContext = valueFactory.createIRI(context);
                        } else {
                            defaultRdfContext = null;
                        }
                        valueFactory.setDefaultContext(defaultRdfContext, overrideRdfContext);
                    }
                    parser.setValueFactory(valueFactory);
                    parser.parse(localIn, localBaseUri);
                } catch (Exception e) {
                    if (allowInvalidIris && skipInvalidLines && !verifyDataTypeValues) {
                        LOG.warn("Exception while parsing RDF", e);
                    } else {
                        throw e;
                    }
                }
            } catch (Exception e) {
                ex = e;
            } finally {
                try {
                    queue.put(END_STATEMENT);
                } catch (InterruptedException ignore) {}
            }
        }

        @Override
        public void handleStatement(Statement st) throws RDFHandlerException {
            if (count == 1 || Math.floorMod(st.hashCode(), count) == offset) {
            	if (!queue.offer(st)) {
            		context.getCounter(Counters.PARSE_QUEUE_FULL).increment(1);
	            	try {
		                queue.put(st);
		            } catch (InterruptedException e) {
		                throw new RDFHandlerException(e);
		            }
            	}
            }
        }

        @Override
        public void handleNamespace(String prefix, String uri) throws RDFHandlerException {
            if (prefix.length() > 0) {
                handleStatement(valueFactory.createStatement(valueFactory.createIRI(uri), HALYARD.NAMESPACE_PREFIX_PROPERTY, valueFactory.createLiteral(prefix), HALYARD.SYSTEM_GRAPH_CONTEXT));
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (in != null) {
                in.close();
                in = null;
            }
            context.getCounter(Counters.LRU_CACHE_HITS).setValue(valueFactory.lruHits);
            context.getCounter(Counters.LRU_CACHE_MISSES).setValue(valueFactory.lruMisses);
            context.getCounter(Counters.LFU_CACHE_HITS).setValue(valueFactory.lfuHits);
            context.getCounter(Counters.LFU_CACHE_MISSES).setValue(valueFactory.lfuMisses);
            context.getCounter(Counters.BOTH_CACHE_HITS).setValue(valueFactory.bothHits);
            context.getCounter(Counters.BOTH_CACHE_MISSES).setValue(valueFactory.bothMisses);
        }

        @Override
        public void warning(String msg, long lineNo, long colNo) {
            LOG.warn(msg);
        }

        @Override
        public void error(String msg, long lineNo, long colNo) {
            LOG.error(msg);
        }

        @Override
        public void fatalError(String msg, long lineNo, long colNo) {
            LOG.error(msg);
        }
    }

    static final class CachingIdValueFactory extends IdValueFactory {
        // LRU cache as formats like Turtle tend to favour recently used identifiers as subjects
        private final LRUCache<String,IRI> lruIriCache = new LRUCache<>(200);
        // LFU cache as predicates tend to have a more global distribution
        private final LFUCache<String,IRI> lfuIriCache = new LFUCache<>(600, 0.2f);
        long lruHits;
        long lruMisses;
        long lfuHits;
        long lfuMisses;
        long bothHits;
        long bothMisses;
    	private IRI defaultContext;
    	private boolean overrideContext;

    	void setDefaultContext(IRI defaultContext, boolean overrideContext) {
			this.defaultContext = defaultContext;
			this.overrideContext = overrideContext;
		}

    	private IRI getOrCreateIRI(String v) {
    		// check both caches
    		IRI lruIri = lruIriCache.get(v);
    		boolean inLru = (lruIri != null);
    		IRI lfuIri = lfuIriCache.get(v);
    		boolean inLfu = (lfuIri != null);
    		// ascertain a value
    		IRI iri = inLru ? lruIri : (inLfu ? lfuIri : super.createIRI(v));
    		// update caches
    		if (inLru) {
    			lruHits++;
    		} else {
    			lruIriCache.put(v, iri);
    			lruMisses++;
    		}
    		if (inLfu) {
    			lfuHits++;
    		} else {
    			lfuIriCache.put(v, iri);
    			lfuMisses++;
    		}
    		if (inLru && inLfu) {
    			bothHits++;
    		} else if (!inLru && !inLfu) {
    			bothMisses++;
    		}
    		return iri;
    	}

    	@Override
    	public IRI createIRI(String iri) {
    		return getOrCreateIRI(iri);
    	}

    	@Override
    	public IRI createIRI(String namespace, String localName) {
    		return getOrCreateIRI(namespace+localName);
    	}

		@Override
        public Statement createStatement(Resource subject, IRI predicate, Value object) {
			return createStatementInternal(subject, predicate, object, defaultContext);
        }

        @Override
        public Statement createStatement(Resource subject, IRI predicate, Value object, Resource context) {
            return createStatementInternal(subject, predicate, object, overrideContext || context == null ? defaultContext : context);
        }

        private Statement createStatementInternal(Resource subject, IRI predicate, Value object, Resource context) {
			if (context != null) {
				return super.createStatement(subject, predicate, object, context);
			} else {
				return super.createStatement(subject, predicate, object);
			}
        }
    }

    private static String listRDF() {
        StringBuilder sb = new StringBuilder();
        for (RDFFormat fmt : RDFParserRegistry.getInstance().getKeys()) {
            sb.append("* ").append(fmt.getName()).append(" (");
            boolean first = true;
            for (String ext : fmt.getFileExtensions()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append('.').append(ext);
            }
            sb.append(")\n");
        }
        return sb.toString();
    }

    public HalyardBulkLoad() {
        super(
            TOOL_NAME,
            "Halyard Bulk Load is a MapReduce application designed to efficiently load RDF data from Hadoop Filesystem (HDFS) into HBase in the form of a Halyard dataset.",
            "Halyard Bulk Load consumes RDF files in various formats supported by RDF4J RIO, including:\n"
                + listRDF()
                + "All the supported RDF formats can be also compressed with one of the compression codecs supported by Hadoop, including:\n"
                + "* Gzip (.gz)\n"
                + "* Bzip2 (.bz2)\n"
                + "* LZO (.lzo)\n"
                + "* Snappy (.snappy)\n"
                + "Example: halyard bulkload -s hdfs://my_RDF_files -w hdfs:///my_tmp_workdir -t mydataset [-g 'http://whatever/graph']"
        );
        addOption("s", "source", "source_paths", SOURCE_PATHS_PROPERTY, "Source path(s) with RDF files, more paths can be delimited by comma, the paths are recursively searched for the supported files", true, true);
        addOption("w", "work-dir", "shared_folder", "Unique non-existent folder within shared filesystem to server as a working directory for the temporary HBase files,  the files are moved to their final HBase locations during the last stage of the load process", true, true);
        addOption("t", "target", "dataset_table", "Target HBase table with Halyard RDF store, target table is created if it does not exist, however optional HBase namespace of the target table must already exist", true, true);
        addOption("i", "allow-invalid-iris", null, ALLOW_INVALID_IRIS_PROPERTY, "Optionally allow invalid IRI values (less overhead)", false, false);
        addOption("d", "verify-data-types", null, VERIFY_DATATYPE_VALUES_PROPERTY, "Optionally verify RDF data type values while parsing", false, false);
        addOption("k", "skip-invalid-lines", null, SKIP_INVALID_LINES_PROPERTY, "Optionally skip invalid lines", false, false);
        addOption("r", "truncate-target", null, TRUNCATE_PROPERTY, "Optionally truncate target table just before the loading the new data", false, false);
        addOption("b", "pre-split-bits", "bits", SPLIT_BITS_PROPERTY, "Optionally specify bit depth of region pre-splits for a case when target table does not exist (default is 3)", false, true);
        addOption("g", "default-named-graph", "named_graph", DEFAULT_CONTEXT_PROPERTY, "Optionally specify default target named graph", false, true);
        addOption("o", "named-graph-override", null, OVERRIDE_CONTEXT_PROPERTY, "Optionally override named graph also for quads, named graph is stripped from quads if --default-named-graph option is not specified", false, false);
        addOption("e", "target-timestamp", "timestamp", TIMESTAMP_PROPERTY, "Optionally specify timestamp of all loaded records (default is actual time of the operation)", false, true);
        addOption("m", "max-split-size", "size_in_bytes", FileInputFormat.SPLIT_MAXSIZE, "Optionally override maximum input split size, where significantly larger single files will be processed in parallel (0 means no limit, default is 200000000)", false, true);
    }

    @Override
    protected int run(CommandLine cmd) throws Exception {
    	configureString(cmd, 's', null);
        String workdir = cmd.getOptionValue('w');
        String target = cmd.getOptionValue('t');
        configureBoolean(cmd, 'i');
        configureBoolean(cmd, 'd');
        configureBoolean(cmd, 'k');
        configureBoolean(cmd, 'r');
        configureInt(cmd, 'b', DEFAULT_SPLIT_BITS);
        configureIRIPattern(cmd, 'g', null);
        configureBoolean(cmd, 'o');
        configureLong(cmd, 'e', System.currentTimeMillis());
        configureLong(cmd, 'm', DEFAULT_SPLIT_MAXSIZE);
        String sourcePaths = getConf().get(SOURCE_PATHS_PROPERTY);
        TableMapReduceUtil.addDependencyJarsForClasses(getConf(),
            NTriplesUtil.class,
            Rio.class,
            AbstractRDFHandler.class,
            RDFFormat.class,
            RDFParser.class);
        HBaseConfiguration.addHbaseResources(getConf());
        Job job = Job.getInstance(getConf(), "HalyardBulkLoad -> " + workdir + " -> " + target);
        job.setJarByClass(HalyardBulkLoad.class);
        job.setMapperClass(RDFMapper.class);
        job.setMapOutputKeyClass(ImmutableBytesWritable.class);
        job.setMapOutputValueClass(KeyValue.class);
        job.setInputFormatClass(RioFileInputFormat.class);
        job.setSpeculativeExecution(false);
		Connection conn = HalyardTableUtils.getConnection(getConf());
		try (Table hTable = HalyardTableUtils.getTable(conn, target, true, getConf().getInt(SPLIT_BITS_PROPERTY, DEFAULT_SPLIT_BITS))) {
			TableName tableName = hTable.getName();
			RegionLocator regionLocator = conn.getRegionLocator(tableName);
			HFileOutputFormat2.configureIncrementalLoad(job, hTable.getDescriptor(), regionLocator);
            FileInputFormat.setInputDirRecursive(job, true);
            FileInputFormat.setInputPaths(job, sourcePaths);
            Path outPath = new Path(workdir);
            FileOutputFormat.setOutputPath(job, outPath);
            TableMapReduceUtil.addDependencyJars(job);
            TableMapReduceUtil.initCredentials(job);
            if (job.waitForCompletion(true)) {
                if (getConf().getBoolean(TRUNCATE_PROPERTY, false)) {
					HalyardTableUtils.clearStatements(conn, tableName);
					hTable.close();
                }
				BulkLoadHFiles.create(getConf()).bulkLoad(tableName, outPath);
                LOG.info("Bulk Load completed.");
                return 0;
            } else {
        		LOG.error("Bulk Load failed to complete.");
                return -1;
            }
        }
    }
}
