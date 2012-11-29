/*
 * Part of the CCNx Java Library.
 *
 * Copyright (C) 2012 Palo Alto Research Center, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.ccnx.ccn.impl.sync;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import org.ccnx.ccn.io.content.SyncNodeComposite;

/**
 * Nodes can be cached by hash across different comparators. We use WeakReferences to avoid accidentally caching nodes that
 * no longer have any real referents.
 * 
 * Since we only need to request nodes once per slice, the pending mechanism should be global
 */
public class SyncNodeCache {
	
	// For holding objects used as locks for each pending hash
	private HashMap<SyncHashEntry, Boolean> _hashesPending = new HashMap<SyncHashEntry, Boolean>();
	
	protected HashMap<SyncHashEntry, WeakReference<SyncNodeComposite>> _nodes = new HashMap<SyncHashEntry, WeakReference<SyncNodeComposite>>();

	/**
	 * Put a newly decoded node into the cache
	 * @param node
	 */
	public void putNode(SyncNodeComposite node) {
		synchronized (this) {
			WeakReference<SyncNodeComposite> wr = new WeakReference<SyncNodeComposite>(node);
			_nodes.put((new SyncHashEntry(node.getHash())), wr);
		}
	}
	
	/**
	 * Get a node associated with a hash if there is one
	 * @param hash
	 * @return
	 */
	public SyncNodeComposite getNode(byte[] hash) {
		if (null == hash)
			return null;
		synchronized (this) {
			WeakReference<SyncNodeComposite> wr = _nodes.get(new SyncHashEntry(hash));
			if (null == wr)
				return null;
			return wr.get();
		}
	}
	
	/**
	 * Activate the mechanism to avoid multiple requests for the same node and to wait for a
	 * node in the process of being fetched by another comparator if it is.
	 * This is a "get and set" routine which creates and stores a lock for sharing if one hasn't
	 * already been created for this hash. The caller must acquire or attempt to acquire the lock.
	 * The first caller should request the node and notify waiters when it has it. Subsequent callers 
	 * will wait for the node to return by acquiring the lock.
	 * 
	 * @param hash
	 * @return Lock object for waiting for the node
	 */
	public Boolean pending(byte[] hash) {
		Boolean lock = null;
		synchronized (this) {
			SyncHashEntry she = new SyncHashEntry(hash);
			lock = _hashesPending.get(she);
			if (null == lock) {
				lock = new Boolean(false);
				_hashesPending.put(she, lock);
			}
			return lock;
		}
	}
		
	/**
	 * Call this after a node has been returned. It releases the semaphore (allowing waiters to
	 * continue) and removes the entry from the array of pending node requests
	 * @param hash
	 */
	public void clearPending(byte[] hash) {
		Boolean lock;
		synchronized (this) {
			SyncHashEntry she = new SyncHashEntry(hash);
			lock = _hashesPending.remove(she);
		}
		if (null != lock) {
			synchronized (lock) {
				lock = false;
				lock.notifyAll();
			}
		}
	}
}
