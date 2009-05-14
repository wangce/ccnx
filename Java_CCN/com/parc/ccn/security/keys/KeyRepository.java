package com.parc.ccn.security.keys;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.parc.ccn.Library;
import com.parc.ccn.config.ConfigurationException;
import com.parc.ccn.config.UserConfiguration;
import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.query.CCNFilterListener;
import com.parc.ccn.data.query.CCNInterestListener;
import com.parc.ccn.data.query.Interest;
import com.parc.ccn.data.security.SignedInfo;
import com.parc.ccn.data.security.KeyLocator;
import com.parc.ccn.data.security.PublisherID;
import com.parc.ccn.data.security.PublisherPublicKeyDigest;
import com.parc.ccn.network.CCNNetworkManager;
import com.parc.security.crypto.certificates.CryptoUtil;

public class KeyRepository implements CCNFilterListener, CCNInterestListener {
	
	protected static final boolean _DEBUG = true;
	public static final long DEFAULT_KEY_TIMEOUT = 2000;
	
	protected  CCNNetworkManager _networkManager = null;
	protected HashMap<ContentName,ContentObject> _keyMap = new HashMap<ContentName,ContentObject>();
	protected HashMap<PublisherPublicKeyDigest, ContentName> _idMap = new HashMap<PublisherPublicKeyDigest,ContentName>();
	
	public KeyRepository() throws IOException {
		_networkManager = new CCNNetworkManager(); // maintain our own connection to the agent, so
			// everyone can ask us for keys
	}
	
	/**
	 * Track an existing key object and make it available.
	 * @param keyObject
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeySpecException 
	 * @throws CertificateEncodingException 
	 */
	public void publishKey(ContentObject keyObject) throws CertificateEncodingException, InvalidKeySpecException, NoSuchAlgorithmException {
		PublicKey key = CryptoUtil.getPublicKey(keyObject.content());
		remember(key, keyObject);
	}
	
	/**
	 * Published a signed record for this key.
	 * @param name
	 * @param key
	 * @param signingKey
	 * @return
	 * @throws ConfigurationException
	 */
	public void publishKey(ContentName keyName, PublicKey key, PublisherPublicKeyDigest keyID, PrivateKey signingKey) throws ConfigurationException {
		byte [] encodedKey = key.getEncoded();
		// Need a key locator to stick in data entry for
		// locator. Could use key itself, but then would have
		// key both in the content for this item and in the
		// key locator, which is redundant. Use naming form
		// that allows for self-referential key names -- the
		// CCN equivalent of a "self-signed cert". Means that
		// we will refer to only the base key name and the publisher ID,
		// not the uniqueified key name...

		KeyLocator locatorLocator = 
			new KeyLocator(keyName, new PublisherID(keyID));

		ContentObject keyObject = null;
		try {
			keyObject = new ContentObject(
					keyName,
					new SignedInfo(keyID,
							SignedInfo.now(),
							SignedInfo.ContentType.KEY,
							locatorLocator),
							encodedKey,
							signingKey);
		} catch (Exception e) {
			BasicKeyManager.generateConfigurationException("Exception generating key locator and publishing key.", e);
		}
		remember(key, keyObject);
	}
	
	public void remember(PublicKey theKey, ContentObject keyObject) {
		PublisherPublicKeyDigest id = new PublisherPublicKeyDigest(theKey);
		_idMap.put(id, keyObject.name());
		_keyMap.put(keyObject.name(), keyObject);
		
		if (_DEBUG) {
			recordKeyToFile(id, keyObject);
		}
		
		// DKS TODO -- do we want to put in a prefix filter...?
		_networkManager.setInterestFilter(this, keyObject.name(), this);
	}

	protected void recordKeyToFile(PublisherPublicKeyDigest id, ContentObject keyObject) {
		File keyDir = new File(UserConfiguration.keyRepositoryDirectory());
		if (!keyDir.exists()) {
			if (!keyDir.mkdirs()) {
				Library.logger().warning("recordKeyToFile: Cannot create user CCN key repository directory: " + keyDir.getAbsolutePath());
				return;
			}
			
		}
		
		// Alas, until 1.6, we can't set permissions on the file or directory...
		// TODO DKS when switch to 1.6, add permission settings.
		File keyFile  = new File(keyDir, id.toString() + ".ccnb");
		if (keyFile.exists()) {
			Library.logger().info("Already stored key " + id.toString() + " to file.");
			// return; // temporarily store it anyway, to overwrite old-format data.
		}
		
		try {
			FileOutputStream fos = new FileOutputStream(keyFile);
			keyObject.encode(fos);
			fos.close();
		} catch (Exception e) {
			Library.logger().info("recordKeyToFile: cannot record key: " + id.toString() + " to file " + keyFile.getAbsolutePath() + " error: " + e.getClass().getName() + ": " + e.getMessage());
			return;
		}
		Library.logger().info("Logged key " + id.toString() + " to file: " + keyFile.getAbsolutePath());
	}
	
	public PublicKey getPublicKey(PublisherPublicKeyDigest desiredKeyID) throws CertificateEncodingException, InvalidKeySpecException, NoSuchAlgorithmException {
		ContentObject keyObject = null;
		ContentName name = _idMap.get(desiredKeyID);
		if (null != name) {
			keyObject = _keyMap.get(name);
			if (null != keyObject)
				return CryptoUtil.getPublicKey(keyObject.content());
		}	
		return null;
	}

	public PublicKey getPublicKey(PublisherPublicKeyDigest desiredKeyID, KeyLocator locator) throws CertificateEncodingException, InvalidKeySpecException, NoSuchAlgorithmException {
	
		// Look for it in our cache first.
		PublicKey publicKey = getPublicKey(desiredKeyID);
		if (null != publicKey) {
			return publicKey;
		}
		
		if (locator.type() != KeyLocator.KeyLocatorType.NAME) {
			Library.logger().info("This is silly: asking the repository to retrieve for me a key I already have...");
			if (locator.type() == KeyLocator.KeyLocatorType.KEY) {
				return locator.key();
			} else if (locator.type() == KeyLocator.KeyLocatorType.CERTIFICATE) {
				return locator.certificate().getPublicKey();
			}
		} else {
			// DKS TODO -- better key retrieval
			/*
			try {
				keyObject = _networkManager.get(new Interest(name), DEFAULT_KEY_TIMEOUT);
			} catch (IOException e) {
				Library.logger().warning("IOException attempting to retrieve key: " + name + ": " + e.getMessage());
				Library.warningStackTrace(e);
			} catch (InterruptedException e) {
				Library.logger().warning("Interrupted attempting to retrieve key: " + name + ": " + e.getMessage());
			}
			if (null != keyObject)
				return CryptoUtil.getPublicKey(keyObject.content());
				*/
		}
		return null;
	}
	
	public ContentObject retrieve(ContentName name, PublisherID publisherID) {
		ContentObject result = _keyMap.get(name);
		if (null != result) {
			if (null != publisherID) {
				if (TrustManager.getTrustManager().matchesRole(
						publisherID,
						result.signedInfo().getPublisherKeyID())) {
					return result;
				}
			}
		}
		return null;
	}
	
	public ContentObject retrieve(Interest interest) {
		ContentObject result = retrieve(interest.name(), interest.publisherID());
		if (null != result)
			return result;
		
		// OK, the straight match didn't cut it; maybe we're just close.
		Iterator<ContentObject> it = _keyMap.values().iterator();
		while (it.hasNext()) {
			ContentObject co = it.next();
			if (interest.matches(co)) {
				// doesn't handle preventing returning same thing over and over
				return co;
				
			}
		}
		return null;
	}

	public int handleInterests(ArrayList<Interest> interests) {
		Iterator<Interest> it = interests.iterator();
		
		while (it.hasNext()) {
			ContentObject keyObject = retrieve(it.next());
			if (null != keyObject) {
				try {
					ContentObject co = new ContentObject(keyObject.name(), keyObject.signedInfo(), keyObject.content(), keyObject.signature()); 
					_networkManager.put(co);
				} catch (Exception e) {
					Library.logger().info("KeyRepository::handleInterests, exception in put: " + e.getClass().getName() + " message: " + e.getMessage());
					Library.infoStackTrace(e);
				}
			}
		}
		return 0;
	}

	public Interest handleContent(ArrayList<ContentObject> results, Interest interest) {
		// TODO Auto-generated method stub
		return null;
	}
}
