/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002-04  David Pavlis <david_pavlis@hotmail.com>
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

// FILE: c:/projects/jetel/org/jetel/data/DelimitedDataFormatter.java

package org.jetel.data.formatter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import org.jetel.data.DataRecord;
import org.jetel.data.Defaults;
import org.jetel.data.Defaults.DataFormatter;
import org.jetel.metadata.DataRecordMetadata;

/**
 * Outputs delimited data record. Handles encoding of characters. Uses NIO classes
 *
 * @author     D.Pavlis
 * @since    July 25, 2002
 * @see        DataFormatter
 */
public class DelimitedDataFormatterNIO implements Formatter {
	
    private final static String DELIMITER_SYSTEM_PROPERTY_NAME="line.separator";
    private String charSet = null;
	private boolean oneRecordPerLinePolicy=false;
	// Attributes
	private DataRecordMetadata metadata;
	private WritableByteChannel writer;
	private CharsetEncoder encoder;
	private String delimiters[];
	private int delimiterLength[];
	private CharBuffer charBuffer;
	private ByteBuffer dataBuffer;
	
	private static char[] NEW_LINE_CHAR;

	// Associations

	// Operations
	
	public DelimitedDataFormatterNIO(){
		dataBuffer = ByteBuffer.allocateDirect(Defaults.DEFAULT_INTERNAL_IO_BUFFER_SIZE);
		charBuffer = CharBuffer.allocate(Defaults.DEFAULT_INTERNAL_IO_BUFFER_SIZE);
		encoder = Charset.forName(Defaults.DataFormatter.DEFAULT_CHARSET_ENCODER).newEncoder();
		encoder.reset();
		NEW_LINE_CHAR=System.getProperty(DELIMITER_SYSTEM_PROPERTY_NAME,"\n").toCharArray();
	}
	
	public DelimitedDataFormatterNIO(String charEncoder){
		dataBuffer = ByteBuffer.allocateDirect(Defaults.DEFAULT_INTERNAL_IO_BUFFER_SIZE);
		charBuffer = CharBuffer.allocate(Defaults.DEFAULT_INTERNAL_IO_BUFFER_SIZE);
		charSet = charEncoder;
		encoder = Charset.forName(charEncoder).newEncoder();
		encoder.reset();
		NEW_LINE_CHAR=System.getProperty(DELIMITER_SYSTEM_PROPERTY_NAME,"\n").toCharArray();
	}
	
	/**
	 *  Description of the Method
	 *
	 * @param  out        Description of Parameter
	 * @param  _metadata  Description of Parameter
	 * @since             March 28, 2002
	 */
	public void open(Object out, DataRecordMetadata _metadata) {
		this.metadata = _metadata;
		
		// create buffered input stream reader 
		writer = ((FileOutputStream) out).getChannel();

		// create array of delimiters & initialize them
		delimiters = new String[metadata.getNumFields()];
		delimiterLength= new int[metadata.getNumFields()];
		for (int i = 0; i < metadata.getNumFields(); i++) {
			delimiters[i] = metadata.getField(i).getDelimiter();
			delimiterLength[i] = delimiters[i].length();
		}
		charBuffer.clear(); // preventively clear the buffer
	}


	/**
	 *  Description of the Method
	 *
	 * @since    March 28, 2002
	 */
	public void close() {
		try{
			flushBuffer();
			writer.close();
		}catch(IOException ex){
			ex.printStackTrace();
		}
	}


	/**
	 *  Description of the Method
	 *
	 * @since    March 28, 2002
	 */
	public void flush() {
		
	}


	/**
	 *  Description of the Method
	 *
	 * @param  record           Description of Parameter
	 * @exception  IOException  Description of Exception
	 * @since                   March 28, 2002
	 */
	public void write(DataRecord record) throws IOException {
		String fieldVal;
		for (int i = 0; i < metadata.getNumFields(); i++) {
			fieldVal=record.getField(i).toString();
			if ((fieldVal.length()+delimiterLength[i]) > charBuffer.remaining())
			{
				flushBuffer();
			}
			charBuffer.put(fieldVal);
			charBuffer.put(delimiters[i]);
		}
		if(oneRecordPerLinePolicy){
		    if (NEW_LINE_CHAR.length > charBuffer.remaining())
			{
				flushBuffer();
			}
			charBuffer.put(NEW_LINE_CHAR);
		}
	}
	
	
	private void flushBuffer() throws IOException {
		CoderResult result;
		charBuffer.flip();
		dataBuffer.clear();
		result=encoder.encode(charBuffer,dataBuffer,false);
		dataBuffer.flip();
		writer.write(dataBuffer);
		charBuffer.clear();
	}

	/**
	 *  Sets OneRecordPerLinePolicy.
	 * @see org.jetel.data.formatter.Formatter#setOneRecordPerLinePolicy(boolean)
	 */
	public void setOneRecordPerLinePolicy(boolean b) {
		oneRecordPerLinePolicy = b;
	}
	
	/**
	 * @return true if formatter should output one record per line
	 */
	public boolean getOneRecordPerLinePolicy() {
		return(this.oneRecordPerLinePolicy);
	}
	
	/**
	 * Returns name of charset which is used by this formatter
	 * @return Name of charset or null if none was specified
	 */
	public String getCharsetName() {
		return(this.charSet);
	}
	
}
/*
 *  end class DelimitedDataFormatter
 */

