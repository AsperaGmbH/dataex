package de.aspera.dataexport.util.dataset.editor;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TableKeysInvestigator {
	private DatabaseMetaData metaData;
	private Connection conn;
	private HashMap<String, String> keyTypeMap;

	public void setConnection(Connection conn) throws TableKeysInvestigatorException {
		this.conn = conn;
		try {
			this.metaData = conn.getMetaData();
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}
	}

	public DatabaseMetaData getMetaData() {
		return metaData;
	}

	public void setMetaData(DatabaseMetaData metaData) {
		this.metaData = metaData;
	}

	public void disableFKeyConstriantCheck() throws TableKeysInvestigatorException {
		Statement stmt;
		try {
			if (metaData.getDatabaseProductName().contains("MySQL")) {
				stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
				stmt.execute("SET FOREIGN_KEY_CHECKS=0;");
			}
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}
	}

	public void enableFKeyConstriantCheck() throws TableKeysInvestigatorException {
		Statement stmt;
		try {
			if (metaData.getDatabaseProductName().contains("MySQL")) {
				stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
				stmt.execute("SET FOREIGN_KEY_CHECKS=1;");
			}
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}
	}

	public HashMap<String, TableConstrainsDescription> createTableConstrainsDescriptions(List<String> tabelNames)
			throws TableKeysInvestigatorException {
		HashMap<String, TableConstrainsDescription> tableConstrians = new HashMap<String, TableConstrainsDescription>();
		for (String tabName : tabelNames) {
			TableConstrainsDescription tabDescription = new TableConstrainsDescription();
			tabDescription.setColumnNamesType(getColumnNamesTypeOfTable(tabName));
			tabDescription.setNumericPrimaryKeyValueMap(getNumericValuesMapForKey("primaryKey", tabName));
			tabDescription.setCharPrimaryKeyValueMap(getCharValuesMapForKey("primaryKey", tabName));
			tabDescription.setUniqueCharColTypeMap(getCharValuesMapForKey("unique", tabName));
			tabDescription.setUniqueNumericColTypeMap(getNumericValuesMapForKey("unique", tabName));
			tabDescription.setReferencedFromTables(getReferencedFromTables(tabName));
			tabDescription.setReferencesToTables(getReferencesToTables(tabName));
			// last step
			keyTypeMap = null;
			tableConstrians.put(tabName, tabDescription);
		}
		return tableConstrians;

	}

	private Map<String, Set<String>> getReferencesToTables(String tabName) throws TableKeysInvestigatorException {
		Map<String, Set<String>> referencesToTabCol = new HashMap<String, Set<String>>();
		Set<String> fKeyTabColNames;
		try {
			ResultSet set = metaData.getImportedKeys(null, null, tabName);
			while (set.next()) {
				String pkTable = set.getString("PKTABLE_NAME");
				String pkcolname = set.getString("PKCOLUMN_NAME");
				String fkCol = set.getString("FKCOLUMN_NAME");
				if (referencesToTabCol.containsKey(fkCol)) {
					referencesToTabCol.get(fkCol).add(pkTable + "." + pkcolname);
				} else {
					fKeyTabColNames = new HashSet<String>();
					fKeyTabColNames.add(pkTable + "." + pkcolname);
					referencesToTabCol.put(fkCol, fKeyTabColNames);
				}
			}
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}

		return referencesToTabCol;
	}

	private Map<String, Set<String>> getReferencedFromTables(String tabName) throws TableKeysInvestigatorException {
		Map<String, Set<String>> referencedFromTabCol = new HashMap<String, Set<String>>();
		Set<String> fKeyTabColNames;
		try {
			ResultSet set = metaData.getExportedKeys(null, null, tabName);
			while (set.next()) {
				String colname = set.getString("PKCOLUMN_NAME");
				String fkTable = set.getString("FKTABLE_NAME");
				String fkCol = set.getString("FKCOLUMN_NAME");
				if (referencedFromTabCol.containsKey(colname)) {
					referencedFromTabCol.get(colname).add(fkTable + "." + fkCol);
				} else {
					fKeyTabColNames = new HashSet<String>();
					fKeyTabColNames.add(fkTable + "." + fkCol);
					referencedFromTabCol.put(colname, fKeyTabColNames);
				}
			}
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}

		return referencedFromTabCol;
	}

	private Map<String, String> getPrimarykeysOfTable(String tableName) throws TableKeysInvestigatorException {
		if (keyTypeMap == null || keyTypeMap.isEmpty())
			keyTypeMap = new HashMap<String, String>();
		ResultSet keySet;
		try {
			keySet = metaData.getPrimaryKeys(null, null, tableName);
			while (keySet.next()) {
				String colName = keySet.getString("COLUMN_NAME");
				ResultSet column = metaData.getColumns(null, null, tableName, colName);
				column.next();
				keyTypeMap.put(colName, column.getString("TYPE_NAME") + "," + column.getString("COLUMN_SIZE"));
			}
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}

		return keyTypeMap;
	}

	/*
	 * This Method can be replaced by finding the Max Number in the Dataset, which
	 * should contain the same Data as the DB
	 */
	private int getMaxNumberValueInColFromDB(String tableName, String colName) throws TableKeysInvestigatorException {
		Statement stmt;
		try {
			stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			ResultSet rs = stmt.executeQuery("select MAX(" + colName + ") as maxNum from " + tableName);
			if (rs.next()) {
				int result = rs.getInt("maxNum");
				stmt.close();
				return result;
			}
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}

		return 0;
	}

	private Set<String> getAllCharValuesInColumnFromDB(String tableName, String colName)
			throws TableKeysInvestigatorException {
		Statement stmt;
		Set<String> resultSet = new HashSet<String>();
		try {
			stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			ResultSet rs = stmt.executeQuery("select " + colName + " as charValue from " + tableName);
			while (rs.next()) {
				String result = rs.getNString("charValue");
				resultSet.add(result);
			}
			stmt.close();
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}

		return resultSet;
	}

	private Map<String, String> getColumnNamesTypeOfTable(String tableName) throws TableKeysInvestigatorException {
		Map<String, String> colNamesType = new HashMap<String, String>();
		ResultSet colNameSet;
		try {
			colNameSet = metaData.getColumns(null, null, tableName, null);
			while (colNameSet.next()) {
				String colName = colNameSet.getString("COLUMN_NAME");
				ResultSet column = metaData.getColumns(null, null, tableName, colName);
				column.next();
				colNamesType.put(colName, column.getString("TYPE_NAME") + "," + column.getString("COLUMN_SIZE"));
			}
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}
		return colNamesType;
	}

	private Map<String, String> getUniqueColumnNames(String tableName, Set<String> primararyKeysNames)
			throws TableKeysInvestigatorException {
		Map<String, String> uniqueColNameTypeMap = new HashMap<String, String>();
		try {
			ResultSet rs = metaData.getIndexInfo(null, null, tableName, true, false);
			while (rs.next()) {
				String colName = rs.getString("COLUMN_NAME");
				if (!primararyKeysNames.contains(colName)) {
					ResultSet column = metaData.getColumns(null, null, tableName, colName);
					column.next();
					uniqueColNameTypeMap.put(colName,
							column.getString("TYPE_NAME") + "," + column.getString("COLUMN_SIZE"));
				}
			}
		} catch (SQLException e) {
			throw new TableKeysInvestigatorException(e.getMessage(), e);
		}
		return uniqueColNameTypeMap;
	}

	private Map<String, Integer> getNumericValuesMapForKey(String keyType, String tableName)
			throws TableKeysInvestigatorException {
		Map<String, Integer> mapIntegerValues = new HashMap<String, Integer>();
		if (keyType.equals("primaryKey")) {
			Map<String, String> primaryKeyNameTypeMap = getPrimarykeysOfTable(tableName);
			for (String keyName : primaryKeyNameTypeMap.keySet()) {
				String typeOfKey = primaryKeyNameTypeMap.get(keyName);
				if (typeOfKey.toLowerCase().contains("number") || typeOfKey.toLowerCase().contains("int")) {
					mapIntegerValues.put(keyName, getMaxNumberValueInColFromDB(tableName, keyName));
				}
			}
		} else if (keyType.equals("unique")) {
			Map<String, String> primaryKeyNameTypeMap = getPrimarykeysOfTable(tableName);
			Map<String, String> uniqueColNameTypeMap = getUniqueColumnNames(tableName, primaryKeyNameTypeMap.keySet());
			for (String keyName : uniqueColNameTypeMap.keySet()) {
				String typeOfKey = uniqueColNameTypeMap.get(keyName);
				if (typeOfKey.toLowerCase().contains("number") || typeOfKey.toLowerCase().contains("int")) {
					mapIntegerValues.put(keyName, getMaxNumberValueInColFromDB(tableName, keyName));
				}
			}
		} else {
			// Foreign Key

		}
		return mapIntegerValues;
	}

	private Map<String, Set<String>> getCharValuesMapForKey(String keyType, String tableName)
			throws TableKeysInvestigatorException {
		Map<String, Set<String>> mapCharValues = new HashMap<String, Set<String>>();
		if (keyType.equals("primaryKey")) {
			Map<String, String> primaryKeyNameTypeMap = getPrimarykeysOfTable(tableName);
			for (String keyName : primaryKeyNameTypeMap.keySet()) {
				String typeOfKey = primaryKeyNameTypeMap.get(keyName);
				if (typeOfKey.toLowerCase().contains("char")) {
					mapCharValues.put(keyName, getAllCharValuesInColumnFromDB(tableName, keyName));
				}
			}
		} else if (keyType.equals("unique")) {
			Map<String, String> primaryKeyNameTypeMap = getPrimarykeysOfTable(tableName);
			Map<String, String> uniqueColNameTypeMap = getUniqueColumnNames(tableName, primaryKeyNameTypeMap.keySet());
			for (String keyName : uniqueColNameTypeMap.keySet()) {
				String typeOfKey = uniqueColNameTypeMap.get(keyName);
				if (typeOfKey.toLowerCase().contains("char")) {
					mapCharValues.put(keyName, getAllCharValuesInColumnFromDB(tableName, keyName));
				}
			}
		} else {
			// Foreign Key

		}
		return mapCharValues;
	}

}
