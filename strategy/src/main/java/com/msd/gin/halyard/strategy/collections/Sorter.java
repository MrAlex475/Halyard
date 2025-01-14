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
package com.msd.gin.halyard.strategy.collections;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.mapdb.DB;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

/**
 * Sorter does not preserve unique instances. All equal instances are mapped to a single instance.
 * This is a MapDB implementation.
 * @author Adam Sotona (MSD)
 * @param <E> Comparable and Serializable element type
 */
public class Sorter<E extends Comparable<E> & Serializable> implements Iterable<Map.Entry<E, Long>>, Closeable {

    private static final String MAP_NAME = "temp";

    private final int memoryThreshold;
    private final Serializer<E> serializer;
    private NavigableMap<E, Long> map;
    private DB db;
    private final long limit;
    private final boolean distinct;
    private long size;

    /**
     * Constructs Sorter with optional limit and optional distinct filtering
     * @param limit long limit, where Long.MAXLONG means no limit
     * @param distinct optional boolean switch to do not preserve multiple equal elements
     * @param memoryThreshold memory usage threshold at which to swap to disk
     * @param serializer custom serializer to use (can be null)
     */
    public Sorter(long limit, boolean distinct, int memoryThreshold, Serializer<E> serializer) {
        this.map = new TreeMap<>();
        this.limit = limit;
        this.distinct = distinct;
        this.memoryThreshold = memoryThreshold;
        this.serializer = serializer;
    }

    public Sorter(long limit, boolean distinct, int memoryThreshold) {
    	this(limit, distinct, memoryThreshold, null);
    }

    /**
     * Adds new element to the sorter.
     * Thread-safe.
     * @param e element to be added to Sorter
     * @throws IOException throws IOException in case of problem with underlying storage
     */
    public synchronized void add(E e) throws IOException {
    	if (map == null) {
    		throw new IOException("Already closed");
    	}

    	if (db == null && map.size() > memoryThreshold) {
    		swapToDisk();
    	}

    	if (size < limit || (!map.isEmpty() && e.compareTo(map.lastKey()) < 0)) {
    		try {
	            Long c = map.get(e);
	            if (c == null) {
	                map.put(e, 1l);
	                size++;
	            } else if (!distinct) {
	                map.put(e, c + 1l);
	                size++;
	            }
	            while (size > limit) {
	                // Discard key(s) that are currently sorted last
	                Map.Entry<E, Long> last = map.lastEntry();
	                long surplus = size - limit;
	                if (last.getValue() > surplus) {
	                    map.put(last.getKey(), last.getValue() - surplus);
	                    size = limit;
	                } else {
	                    map.remove(last.getKey());
	                    size -= last.getValue();
	                }
	            }
	        } catch (IllegalAccessError err) {
	            throw new IOException(err);
	        }
    	}
    }

    private synchronized void swapToDisk() {
    	if (db != null) {
    		return;
    	}

    	db = DBMaker.newTempFileDB().deleteFilesAfterClose().closeOnJvmShutdown().mmapFileEnableIfSupported().transactionDisable().asyncWriteEnable().make();
    	BTreeMapMaker mapMaker = db.createTreeMap(MAP_NAME);
    	if (serializer != null) {
    		mapMaker = mapMaker.keySerializerWrap(serializer);
    	}
        NavigableMap<E,Long> dbMap = mapMaker.make();
        dbMap.putAll(map);
        map = dbMap;
    }

    @Override
    public synchronized Iterator<Map.Entry<E, Long>> iterator() {
        return map.entrySet().iterator();
    }

    @Override
    public synchronized void close() {
    	map = null;
    	if (db != null) {
	        try {
	            db.close();
	        } catch (IllegalAccessError|IllegalStateException ignore) {
	            //silent close
	        } finally {
	        	db = null;
	        }
    	}
    }
}
