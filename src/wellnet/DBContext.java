package wellnet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import wellnet.dao.UserAccount;
import wellnet.dao.Wine;
import wellnet.dao.WineTranslation;
import wellnet.dao.WineryBio;
import wellnet.dao.WineryBioTranslation;

//use this class to establish a connection to the database
public class DBContext {

	private Properties properties = new Properties();
	private String driver = "";
	private static String urlConnectionString = "";
	private static String username = "";
	private static String password = "";
	private final String createSql = "/sqlScripts/WellnetCreate.sql";
	private final String intiSql = "/sqlScripts/initData.sql";
	
	/*
	 * I have had trouble with class level connections in the past, i.e. they must be set 
	 * to null at the end of each method or SQL exceptions may occur on their next use. This is
	 * certainly only a concern if we are using a single instance of this class to execute multiple statements.
	 * -Alex
	 * 
	 * I had forgotten to include the close() method when posting this class. However I like the idea of creating a new
	 * connection for each method. That way we don't have to worry about closing it each time we use it outside this class.
	 * -Jeff
	 */

	// Constructor
	public DBContext() throws IOException, SQLException, ClassNotFoundException {
		
			// Uses the jbdc driver to create a new connection to the database			
			properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("../config.properties"));
			urlConnectionString = properties.getProperty("jdbc.connectionURL");
			username = properties.getProperty("jdbc.username");
			password = properties.getProperty("jdbc.password");
			driver = properties.getProperty("jdbc.driver");
			
			Class.forName(driver);
		
	}
	
	public String getScriptFilePath(String action){
		String filePath = "";
		switch(action.toLowerCase()){
		case "create":
			filePath = this.createSql;
			break;
		case "init":
			filePath = this.createSql;
			break;
		}
		return filePath;
	}
	
	public int[] executeSqlFile(String filePath) throws URISyntaxException, SQLException, IOException{
		Connection connection = null;
		String line = null;
		
		StringBuffer stringBuffer = new StringBuffer();
		try{
			File sqlFile = new File(new URI(filePath));
			FileReader fileReader = new FileReader(sqlFile);
			BufferedReader reader = new BufferedReader(fileReader);
			while((line = reader.readLine()) != null){
				stringBuffer.append(String.format("%s\n", line));
			}
			
			connection = DriverManager.getConnection(urlConnectionString, username, password);
			PreparedStatement preparedStatement = connection.prepareStatement(stringBuffer.toString());
			int[] results = preparedStatement.executeBatch();
			reader.close();
			fileReader.close();
			return results;
		}finally{
			
			connection.close();
		}
		
	}
	
	/**
	 * In case we need to use the account ID for something.
	 * @return the next int in the SEQ_ACCOUNT_TYPE sequence
	 * @throws SQLException
	 */
	public int getNextAccountId() throws SQLException {
		
		Connection connection = null;
		int accountId = 0;
		
		try{
			
			connection = DriverManager.getConnection(urlConnectionString, username, password);
			
			String mySQL = "SELECT SEQ_ACCOUNT_TYPE.NEXTVAL FROM DUAL ";
			PreparedStatement preparedStatement = connection.prepareStatement(mySQL);
			ResultSet rs = preparedStatement.executeQuery();
			
			if(rs.next()){
				accountId = rs.getInt(1);
			}
			
			return accountId;
		}finally{
			connection.close();
		}
	}
	
	// ***********This method needs to be parameterized************
	public void addUserAccount(UserAccount userAccount) throws SQLException{
		
		Connection connection = null;
		
		try{
			connection = DriverManager.getConnection(urlConnectionString, username, password);
			
			String sql = "INSERT INTO USER_ACCOUNT VALUES('"+ userAccount.getUserId() +"','"+ userAccount.getUsername() +"','"+ 
															userAccount.getPswd() +"','"+ userAccount.getAccountId() +"')";
		
			// Creates a new statement and executes the SQL query
			Statement statement = connection.createStatement();
			statement.executeUpdate(sql);			
			statement.close();
		}finally{
			connection.close();
		}
	}
	
	// ***********This method needs to be parameterized************
	public void addWine(Wine wine){
				//Creates insert statement
				String sql = "INSERT INTO WINE VALUES(seq_wine.nextval,'"+ wine.getName() +"','"+ wine.getYear() +"','"+ 
														wine.getType() +"','"+ wine.getStock()+"','"+ wine.getPromoMaterials() +"','"+ 
														wine.getPairingTastingNotes() +"','"+ wine.getAccountId() +"')";
				
				Connection connection = null;
				
				try {
					connection = DriverManager.getConnection(urlConnectionString, username, password);
					// Creates a new statement and executes the SQL query
					Statement statement = connection.createStatement();
					statement.executeUpdate(sql);			
					statement.close();
				} catch (SQLException e) {
					e.printStackTrace();
					//DisplayErrorMessage("There was an error executing the query on the database");
				}
			}
	// ***********This method needs to be parameterized************
	public void addBio(WineryBio wineryBio){
		String sql = "INSERT INTO WINERY_BIO VALUES('"+ wineryBio.getAccountId() +"','"+ wineryBio.getBio() +"')";
		
		Connection connection = null;
		
		try {
			connection = DriverManager.getConnection(urlConnectionString, username, password);
			// Creates a new statement and executes the SQL query
			Statement statement = connection.createStatement();
			statement.executeUpdate(sql);			
			statement.close();
		} catch (SQLException e) {
			e.printStackTrace();
			//DisplayErrorMessage("There was an error executing the query on the database");
		}
	}
	
	/**
	 * Returning a list is likely not necessary.
	 * @param accountId
	 * @param language
	 * @return List of all WineryBio associated with the accountId and the given language
	 * @throws SQLException
	 */
	public List<WineryBio> getWineryBio(int accountId, String language) throws SQLException{
		
		Connection connection = null;
		
		try{
			boolean isEnglish = (language.equalsIgnoreCase("english") || language.equalsIgnoreCase("default"));
			
			StringBuilder selectStatement = new StringBuilder();
			String tableName = isEnglish ? "WINERY_BIO" : "BIO_TRANSLATION"; 
			
			selectStatement.append(buildSqlSelectFromStatement(tableName));
			
			connection = DriverManager.getConnection(urlConnectionString, username, password);
			PreparedStatement preparedStatement;
			
			if(isEnglish){
				selectStatement.append("WHERE ACCOUNT_ID = ? ");
				preparedStatement = connection.prepareStatement(selectStatement.toString());
				preparedStatement.setInt(1, accountId);
			}else{
				selectStatement.append("WHERE ACCOUNT_ID = ? AND LANGUAGE = ? ");
				preparedStatement = connection.prepareStatement(selectStatement.toString());
				preparedStatement.setInt(1, accountId);
				preparedStatement.setString(2, language);
			}
			
			ResultSet rs = preparedStatement.executeQuery();
			
			return fillBioList(rs, tableName);
				
		}finally{
			connection.close();
		}
	}
	
	public List<Wine> getWineryWineStock(int accountId, String language) throws SQLException{
		
		Connection connection = null;
		
		try{
			boolean isEnglish = (language.equalsIgnoreCase("english") || language.equalsIgnoreCase("default"));
			
			StringBuilder selectStatement = new StringBuilder();
			String tableName = isEnglish ? "WINE" : "WINE_TRANSLATION"; 
			
			selectStatement.append(buildSqlSelectFromStatement(tableName));
			
			connection = DriverManager.getConnection(urlConnectionString, username, password);
			PreparedStatement preparedStatement;
			
			if(isEnglish){
				selectStatement.append("WHERE ACCOUNT_ID = ? ");
				preparedStatement = connection.prepareStatement(selectStatement.toString());
				preparedStatement.setInt(1, accountId);
			}else{
				selectStatement.append("WHERE ACCOUNT_ID = ? AND LANGUAGE = ? ");
				preparedStatement = connection.prepareStatement(selectStatement.toString());
				preparedStatement.setInt(1, accountId);
				preparedStatement.setString(2, language);
			}
			
			ResultSet rs = preparedStatement.executeQuery();
			
			return fillWineList(rs, tableName);
				
		}finally{
			connection.close();
		}
	}
	
	private List<WineryBio> fillBioList(ResultSet rs, String tableName) throws IllegalArgumentException, SQLException{
		
		List<WineryBio> wineryBio = new ArrayList<WineryBio>();
		
		if(tableName.equalsIgnoreCase("WINERY_BIO")){
			
			while(rs.next()){
				wineryBio.add(new WineryBio(rs.getInt(WineryBio.ColumnNames[0]),rs.getString(WineryBio.ColumnNames[1])));
			}
			
		}else if(tableName.equalsIgnoreCase("BIO_TRANSLATION")){
			
			while(rs.next()){
				wineryBio.add(new WineryBioTranslation(rs.getInt(WineryBioTranslation.ColumnNames[0]),rs.getInt(WineryBioTranslation.ColumnNames[1]),
														rs.getString(WineryBioTranslation.ColumnNames[1]), rs.getString(WineryBioTranslation.ColumnNames[3])));
			}
			
		}else{
			throw new IllegalArgumentException("Table must be WINERY_BIO or BIO_TRANSLATION");
		}
		
		return wineryBio;
	}
	
	private List<Wine> fillWineList(ResultSet rs, String tableName) throws IllegalArgumentException, SQLException{

		List<Wine> wineStock = new ArrayList<Wine>();
		
		if(tableName.equalsIgnoreCase("WINE")){
			
			while(rs.next()){
				wineStock.add(new Wine(rs.getInt(Wine.ColumnNames[0]),rs.getString(Wine.ColumnNames[1]),rs.getInt(Wine.ColumnNames[2]),
									rs.getString(Wine.ColumnNames[3]),rs.getInt(Wine.ColumnNames[4]),rs.getString(Wine.ColumnNames[5]),
									rs.getString(Wine.ColumnNames[6]),rs.getInt(Wine.ColumnNames[7])));
			}
			
		}else if(tableName.equalsIgnoreCase("WINE_TRANSLATION")){
			
			while(rs.next()){
				wineStock.add(new WineTranslation(rs.getInt(WineTranslation.ColumnNames[0]),rs.getInt(WineTranslation.ColumnNames[1]),
													rs.getString(WineTranslation.ColumnNames[2]),rs.getString(WineTranslation.ColumnNames[3]),
													rs.getInt(WineTranslation.ColumnNames[4]),rs.getString(WineTranslation.ColumnNames[5]),
													rs.getInt(WineTranslation.ColumnNames[6]),rs.getString(WineTranslation.ColumnNames[7]),
													rs.getString(WineTranslation.ColumnNames[8]),rs.getInt(WineTranslation.ColumnNames[9])));
			}
			
		}else{
			throw new IllegalArgumentException("Table must be WINE or WINE_TRANSLATION");
		}
		
		return wineStock;
			
	}
	
	/**
	 * This method was just me toying with Oracle. We can delete and use the static
	 * column names in each class if it gives us any problems.
	 * @param tableName
	 * @return String of SELECT statement with all columns and FROM table
	 * @throws SQLException
	 */
	private String buildSqlSelectFromStatement(String tableName) throws SQLException{

		Connection connection = null;
		
		try{
			
			connection = DriverManager.getConnection(urlConnectionString, username, password);
			
			String selectColumns = "SELECT COLUMN_NAME FROM USER_TAB_COLS WHERE TABLE_NAME = ? ";
			PreparedStatement preparedStatement = connection.prepareStatement(selectColumns);
			preparedStatement.setString(1, tableName);
			ResultSet rs = preparedStatement.executeQuery();
			
			
			StringBuilder returnSql = new StringBuilder("SELECT ");
			
			while(rs.next()){
				returnSql.append(rs.getString("COLUMN_NAME")).append(" ");
			}
			
			returnSql.append("FROM ").append(tableName).append(" ");
			
	
			return returnSql.toString();

		}finally{
			connection.close();
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}