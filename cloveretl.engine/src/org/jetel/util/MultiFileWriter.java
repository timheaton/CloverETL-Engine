/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2005-06  Javlin Consulting <info@javlinconsulting.cz>
*    
*    This library is free software; you can redistribute it and/or
*    modify it under the terms of the GNU Lesser General Public
*    License as published by the Free Software Foundation; either
*    version 2.1 of the License, or (at your option) any later version.
*    
*    This library is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
*    Lesser General Public License for more details.
*    
*    You should have received a copy of the GNU Lesser General Public
*    License along with this library; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/
package org.jetel.util;

import java.io.IOException;
import java.net.URL;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.log4j.Logger;
import org.jetel.data.DataRecord;
import org.jetel.data.RecordKey;
import org.jetel.data.formatter.Formatter;
import org.jetel.data.formatter.provider.FormatterProvider;
import org.jetel.data.lookup.LookupTable;
import org.jetel.enums.PartitionFileTagType;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.metadata.DataRecordMetadata;

/**
 * Class for transparent writing into multifile or multistream. Underlying formatter is used for formatting
 * incoming data records and destination is a list of files defined in fileURL attribute
 * by org.jetel.util.MultiOutFile or iterator of writable channels.
 * The MultiFileWriter can partition data according to lookup table and partition key or partition key only.
 * Usage: 
 * - first instantiate some suitable formatter, set all its parameters (don't call init method)
 * - optionally set appropriate logger
 * - sets required multifile writer parameters (setAppendData(), setRecordsPerFile(), setBytesPerLine(), ...)
 * - call init method with metadata for reading input sources
 * - at last one can use this writer in the same way as all formatter via write method called in cycle

 * @author Martin Zatopek (martin.zatopek@javlinconsulting.cz)
 *         (c) Javlin Consulting (www.javlinconsulting.cz)
 *
 * @created 2.11.2006
 */
public class MultiFileWriter {
	private static final Logger log = Logger.getLogger(MultiFileWriter.class);

	// Default capacity of HashMap
	private final static int tableInitialSize = 512;

    private Formatter currentFormatter;				// for former constructor
    private FormatterProvider formatterGetter;		// creates new formatter
    private Map<Object, TargetFile> multiTarget;	// <key, TargetFile> for file partition
    private TargetFile currentTarget;				// actual output target
    private URL contextURL;
    private String fileURL;
    private int recordsPerFile;
    private int bytesPerFile;
    private boolean appendData;
    private Iterator<WritableByteChannel> channels;
    /** counter which decreases with each skipped record */
    private int skip;
    /** fixed value */
    private int skipRecords;
	private int numRecords;
	private int counter;
	private boolean useChannel = true;
	private DataRecordMetadata metadata;
    
	private LookupTable lookupTable = null;
	private String[] partitionKeyNames;
	private RecordKey partitionKey;
	private String[] partitionOutFields;
	private int[] iPartitionOutFields;
	
	private boolean useNumberFileTag = true;
	private int numberFileTag;
	
    /**
     * Constructor.
     * @param formatter formatter is used for incoming records formatting
     * @param fileURL target file(s) definition
     */
    public MultiFileWriter(Formatter formatter, URL contextURL, String fileURL) {
        this.currentFormatter = formatter;
        this.contextURL = contextURL;
        this.fileURL = fileURL;
    }

    public MultiFileWriter(Formatter formatter, Iterator<WritableByteChannel> channels) {
        this.currentFormatter = formatter;
        this.channels = channels;
    }

    public MultiFileWriter(FormatterProvider formatterGetter, URL contextURL, String fileURL) {
        this.formatterGetter = formatterGetter;
        this.contextURL = contextURL;
        this.fileURL = fileURL;
    }

    public MultiFileWriter(FormatterProvider formatterGetter, Iterator<WritableByteChannel> channels) {
        this.formatterGetter = formatterGetter;
        this.channels = channels;
    }

    /**
     * Initializes underlying formatter with a given metadata.
     * 
     * @param metadata
     * @throws ComponentNotReadyException 
     * @throws IOException 
     */
    public void init(DataRecordMetadata metadata) throws ComponentNotReadyException {
    	this.metadata = metadata;
    	preparePatitionKey();	// initialize partition key - if defined
    	prepareTargets();		// prepare output targets
    }
    
    /**
	 * Reset writer for next graph execution. 
     * @throws ComponentNotReadyException 
     */
	public void reset() throws ComponentNotReadyException {
		log.info("reset"+ this + " "+this.currentTarget);
		counter = 0;
		numberFileTag = 0;
		skip = skipRecords;

		if (lookupTable != null){
			lookupTable.reset();
		}
		if (multiTarget != null){
			multiTarget.clear();
		}else{
			currentTarget.reset();
		}
	}
	
    /**
     * Creates target array or target map.
     * 
     * @throws ComponentNotReadyException
     */
    private void prepareTargets() throws ComponentNotReadyException {
    	// prepare type of targets: lookpup/keyValue
		if (partitionKey != null) {
			multiTarget = new HashMap<Object, TargetFile>(tableInitialSize);
			if (lookupTable != null) {
				lookupTable.setLookupKey(partitionKey);
			}
			
		// prepare type of targets: single
		} else {
			if (currentFormatter == null) {
				currentFormatter = formatterGetter.getNewFormatter();	
			}
    		currentFormatter.init(metadata);
    		if (currentTarget != null)
    			currentTarget.close();
    		currentTarget = createNewTarget(currentFormatter);
    		try {
				currentTarget.init();
			} catch (IOException e) {
				throw new ComponentNotReadyException(e);
			}
		}
    }
    
    /**
     * Creates new target according to fileURL or channels. 
     * Sets append data and uses channel properties.
     * 
     * @return target
     */
    private TargetFile createNewTarget() {
    	TargetFile targetFile;
		if (fileURL != null) targetFile = new TargetFile(fileURL, contextURL, formatterGetter, metadata);
		else targetFile = new TargetFile(channels, formatterGetter, metadata);
		targetFile.setAppendData(appendData);
		targetFile.setUseChannel(useChannel);
		return targetFile;
    }
    
    /**
     * Creates new target according to fileURL or channels for the formatter. 
     * Sets append data and uses channel properties.
     * 
     * @return target
     */
    private TargetFile createNewTarget(Formatter formatter) {
    	TargetFile targetFile;
		if (fileURL != null) 
			targetFile = new TargetFile(fileURL, contextURL, formatter, metadata);
		else 
			targetFile = new TargetFile(channels, formatter, metadata);
		targetFile.setAppendData(appendData);
		targetFile.setUseChannel(useChannel);
		return targetFile;
    }

    
    /**
     * Checks and prepares partition key. 
     * 
     * @param metadata
     * @throws ComponentNotReadyException
     */
    private void preparePatitionKey() throws ComponentNotReadyException {
    	if (partitionKeyNames == null && partitionKey == null && lookupTable != null) {
    		throw new ComponentNotReadyException("Lookup table is not properly defined. The partition key is missing.");
    	}
	    if (partitionKeyNames != null) {
			partitionKey = new RecordKey(partitionKeyNames, metadata);
		}
	    
    	DataRecordMetadata metadata;
    	if (lookupTable != null) {
    		metadata = lookupTable.getMetadata();
    	} else {
    		metadata = this.metadata;
    	}
    	preparePartitionOutFields(metadata);
    	
	    if (partitionKey != null) {
			try {
				partitionKey.init();
			} catch (Exception e) {
				throw new ComponentNotReadyException(e.getMessage());
			}
	    }
    }

    /**
     * Writes given record via formatter into destination file(s).
     * @param record
     * @throws IOException
     * @throws ComponentNotReadyException 
     */
    public void write(DataRecord record) throws IOException, ComponentNotReadyException {
        // check for index of last returned record
        if(numRecords > 0 && numRecords == counter) {
            return;
        }
        
        // shall i skip some records?
        if(skip > 0) {
            skip--;
            return;
        }
        if (currentTarget != null) {
        	checkAndSetNextOutput();
        }
        
        // write the record according to value partition
        if (partitionKey == null) {
            // single formatter/getter
        	writeRecord2CurrentTarget(record);
        } else {
        	if (lookupTable != null) {
                // write the record according to lookup table
            	writeRecord4LookupTable(record);
            } else {
            	// just partition key without lookup table
            	writeRecord2MultiTarget(record, record);
            }
        }
    }

    /**
     * Writes the data record according to value partition.
     * 
     * @param record
     * @throws IOException
     * @throws ComponentNotReadyException
     */
    private final void writeRecord2MultiTarget(DataRecord keyRecord, DataRecord record) throws IOException, ComponentNotReadyException {
    	String keyString = getKeyString(keyRecord);
    	if ((currentTarget = multiTarget.get(keyString)) == null) {
    		currentTarget = createNewTarget();
    		currentTarget.setFileTag(useNumberFileTag ? numberFileTag++ : keyString);
    		currentTarget.init();
    		multiTarget.put(keyString, currentTarget);
    	}
		currentFormatter = currentTarget.getFormatter();
		writeRecord2CurrentTarget(record);
    }
    
    private String getKeyString(DataRecord record) {
    	StringBuffer sb = new StringBuffer();
    	for (int pos: iPartitionOutFields) {
    		sb.append(record.getField(pos).getValue());    		
    	}
    	return sb.toString();
    }
    
    private void preparePartitionOutFields(DataRecordMetadata metadata) throws ComponentNotReadyException {
    	if (partitionOutFields != null) {
        	iPartitionOutFields = new int[partitionOutFields.length];
        	int pos;
        	for (int i=0; i<iPartitionOutFields.length; i++) {
        		pos = metadata.getFieldPosition(partitionOutFields[i]);
        		if (pos == -1) throw new ComponentNotReadyException("Field name '" + partitionOutFields[i] + "' not found in partition object (lookup table or partition key)");
        		iPartitionOutFields[i] = pos;
        	}
    	} else if (partitionKey != null) {
    		iPartitionOutFields = partitionKey.getKeyFields();
    	}
    }
    
    /**
     * Writes the data record according to lookup table.
     * 
     * @param record
     * @throws IOException
     * @throws ComponentNotReadyException
     */
    private final void writeRecord4LookupTable(DataRecord record) throws IOException, ComponentNotReadyException {
    	DataRecord keyRecord = lookupTable.get(record);
    	
		// data filtering
    	while (keyRecord != null) {
			writeRecord2MultiTarget(keyRecord, record);
			
			// get next record from database with the same key
			if ((keyRecord = lookupTable.getNext()) != null) {
		        checkAndSetNextOutput();
			}
    	}
    }
    
    /**
     * Sets next output if records or bytes for the output are exceeded. 
     * 
     * @throws IOException
     */
    private final void checkAndSetNextOutput() throws IOException {
        if ((recordsPerFile > 0 && currentTarget.getRecords() >= recordsPerFile)
                || (bytesPerFile > 0 && currentTarget.getBytes() >= bytesPerFile)) {
        	currentTarget.setNextOutput();
        }
    }
    
    /**
     * Writes data into formatter and sets byte and record counters.
     * 
     * @param record
     * @throws IOException
     */
    private final void writeRecord2CurrentTarget(DataRecord record) throws IOException {
    	currentTarget.setBytes(currentTarget.getBytes()+currentFormatter.write(record));
    	currentTarget.setRecords(currentTarget.getRecords()+1);
        counter++;
    }
    
    /**
     * Closes underlying formatter.
     */
    public void close() {
    	if (multiTarget != null) {
        	for (Entry<Object, TargetFile> entry: multiTarget.entrySet()) {
        		entry.getValue().close();
        	}
    	} else {
    		currentTarget.close();
    	}
    }

    /**
     * Sets number of bytes written into separate file.
     * @param bytesPerFile
     */
    public void setBytesPerFile(int bytesPerFile) {
        this.bytesPerFile = bytesPerFile;
    }

    /**
     * Sets number of records written into seprate file.
     * @param recordsPerFile
     */
    public void setRecordsPerFile(int recordsPerFile) {
        this.recordsPerFile = recordsPerFile;
    }
    
    
    public void setLogger(Log logger) {
    	TargetFile.setLogger(logger);
    }

    public void setAppendData(boolean appendData) {
        this.appendData = appendData;
    }

    /**
     * Sets number of skipped records in next call of getNext() method.
     * @param skip
     */
    public void setSkip(int skip) {
        this.skip = skip;
        this.skipRecords = skip;
    }

    /**
     * Sets number of read reacords
     * @param numRecords
     */
    public void setNumRecords(int numRecords) {
        this.numRecords = numRecords;
    }

	public boolean isUseChannel() {
		return useChannel;
	}

	public void setUseChannel(boolean useChannel) {
		this.useChannel = useChannel;
	}

	/**
	 * 
	 * 
	 * @param partitionKeyNames
	 */
	public void setPartitionKeyNames(String[] partitionKeyNames) {
		this.partitionKeyNames = partitionKeyNames;
	}

	/**
	 * Gets partition key names used for partition key.
	 * 
	 * @return partitionKeyNames
	 */
	public String[] getPartitionKeyNames() {
		return partitionKeyNames;
	}

	/**
	 * Sets record key used for partition.
	 * 
	 * @param partitionKey
	 */
	public void setPartitionKey(RecordKey partitionKey) {
		this.partitionKey = partitionKey;
	}

	/**
	 * Gets record key used for partition.
	 * 
	 * @return RecordKey
	 */
	public RecordKey getPartitionKey() {
		return partitionKey;
	}
	
	/**
	 * Sets lookup table.
	 * 
	 * @param lookupTable - LookupTable
	 */
	public void setLookupTable(LookupTable lookupTable) {
		this.lookupTable = lookupTable;
	}

	/**
	 * Gets lookup table.
	 * 
	 * @return LookupTable
	 */
	public LookupTable getLookupTable() {
		return lookupTable;
	}
	
	/**
	 * Gets partition file tag type
	 * 
	 * @return PartitionFileTagType
	 */
	public boolean getPartitionFileTag() {
		return useNumberFileTag;
	}

	/**
	 * Sets partition file tag type
	 *
	 * @param partitionFileTagType - PartitionFileTagType
	 */
	public void setPartitionFileTag(PartitionFileTagType partitionFileTagType) {
		this.useNumberFileTag = partitionFileTagType == PartitionFileTagType.NUMBER_FILE_TAG;
	}

	/**
	 * Sets field names for lookup table.
	 * 
	 * @param partitionOutFields
	 */
	public void setPartitionOutFields(String[] partitionOutFields) {
		this.partitionOutFields = partitionOutFields;
	}

	public void setChannels(Iterator<WritableByteChannel> channels) {
		this.channels = channels;
	}	
	
}
