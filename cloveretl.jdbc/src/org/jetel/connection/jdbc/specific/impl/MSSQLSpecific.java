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
package org.jetel.connection.jdbc.specific.impl;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;

import org.jetel.connection.jdbc.DBConnection;
import org.jetel.connection.jdbc.specific.conn.MSSQLConnection;
import org.jetel.exception.JetelException;
import org.jetel.metadata.DataFieldMetadata;

/**
 * MS SQL 2008 specific behaviour.
 * 
 * This specific works primarily on SQL Server 2008 and above
 * although most of the features work on SQL Server 2005 and older
 * 
 * @modified Pavel Najvar (pavel.najvar@javlin.eu) Mar 2009
 * @author Martin Zatopek (martin.zatopek@javlinconsulting.cz)
 *         (c) Javlin Consulting (www.javlinconsulting.cz)
 *
 * @created Jun 3, 2008
 */
public class MSSQLSpecific extends AbstractJdbcSpecific {

	private static final MSSQLSpecific INSTANCE = new MSSQLSpecific();
	
	protected MSSQLSpecific() {
		super(AutoGeneratedKeysType.SINGLE);
	}

	public static MSSQLSpecific getInstance() {
		return INSTANCE;
	}

	/* (non-Javadoc)
	 * @see org.jetel.connection.jdbc.specific.impl.AbstractJdbcSpecific#createSQLConnection(org.jetel.connection.jdbc.DBConnection, org.jetel.connection.jdbc.specific.JdbcSpecific.OperationType)
	 */
	@Override
	public Connection createSQLConnection(DBConnection connection, OperationType operationType) throws JetelException {
		return new MSSQLConnection(connection, operationType);
	}

    public String quoteIdentifier(String identifier) {
        return ('[' + identifier + ']');
    }

	public String sqlType2str(int sqlType) {
		switch(sqlType) {
		case Types.TIMESTAMP :
			return "DATETIME";
		case Types.BOOLEAN :
			return "TINYINT";
		case Types.INTEGER :
			return "INT";
		case Types.NUMERIC :
			return "FLOAT";
		}
		return super.sqlType2str(sqlType);
	}

	@Override
	public String jetelType2sqlDDL(DataFieldMetadata field) {
		switch(jetelType2sql(field)) {
		case Types.BOOLEAN :
			return "TINYINT(1)";
		}
		return super.jetelType2sqlDDL(field);
	}

	@Override
	public ArrayList<String> getSchemas(DatabaseMetaData dbMeta)
			throws SQLException {
		return AbstractJdbcSpecific.getMetaCatalogs(dbMeta);
	}
    
	
}
