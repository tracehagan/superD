/*******************************************************************************
 *  Copyright 2013 Jason Sipula, Trace Hagan                                   *
 *                                                                             *
 *  Licensed under the Apache License, Version 2.0 (the "License");            *
 *  you may not use this file except in compliance with the License.           *
 *  You may obtain a copy of the License at                                    *
 *                                                                             *
 *      http://www.apache.org/licenses/LICENSE-2.0                             *
 *                                                                             *
 *  Unless required by applicable law or agreed to in writing, software        *
 *  distributed under the License is distributed on an "AS IS" BASIS,          *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 *  See the License for the specific language governing permissions and        *
 *  limitations under the License.                                             *
 *******************************************************************************/

package net.snakedoc.superd;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.log4j.Logger;

import net.snakedoc.jutils.Config;
import net.snakedoc.jutils.ConfigException;
import net.snakedoc.jutils.database.H2;

public class CheckDupes {
    //TODO FIX CHECKDUPES
    //SHOWS ONE SET OF DUPLICATE FILES (I.E. FILE1 = FILE2) TWICE: File1=File2 and File2=File1
    private static final Logger log = Logger.getLogger(CheckDupes.class);
    
	public static void main(String[] args) {
		CheckDupes cd = new CheckDupes();
		cd.checkDupes();
	}
	public void checkDupes() {
	    Config config = new Config("props/superD.properties");
	    config.loadConfig("props/log4j.properties");
	    // SQL statements
	    String sqlCount = "SELECT COUNT(*) FROM files";
		String sqlGetHashes = "SELECT file_hash, file_path FROM files";
		String sqlCompare = "SELECT record_id, file_hash, file_path FROM files " +
				"WHERE file_hash = ? AND file_path != ?";
		String sqlGetRecordId = "SELECT record_id FROM files WHERE file_path = ?";
		String sqlInsertDupes = "INSERT INTO duplicates (dupe1_id, dupe2_id, file_hash) VALUES(? , ? , ?)";
		String sqlCountBytes = "SELECT SUM(files.file_size) FROM files JOIN duplicates ON files.record_id = duplicates.dupe1_id;";
        String sqlAlreadyChecked = "SELECT COUNT(*) FROM duplicates WHERE file_hash = ?";
        String sqlSelectDuplicateHashes = "INSERT INTO nonUnique (file_path, file_hash, file_size) SELECT  file_path, file_hash, file_size FROM files WHERE file_hash IN (SELECT file_hash FROM files GROUP BY file_hash HAVING COUNT(*) > 1)";
        String sqlSelectUnique = "INSERT INTO signatures (file_hash, file_size) SELECT DISTINCT file_hash, file_size FROM nonUnique";
		
		// Prepared Statements (NULL)
		PreparedStatement psCount = null;
		PreparedStatement psGetHashes = null;
		PreparedStatement psCompare = null;
		PreparedStatement psGetRecordId = null;
		PreparedStatement psInsertDupes = null;
		PreparedStatement psCountBytes = null;
        PreparedStatement psAlreadyChecked = null;
        PreparedStatement psSelectDuplicateHashes = null;
        PreparedStatement psSelectUnique = null;
		
		// Result Sets (NULL)
		ResultSet rsCount = null;
		ResultSet rsGetHashes = null;
		ResultSet rsCompare = null;
		ResultSet rsGetRecordId = null;
		ResultSet rsCountBytes = null;
        ResultSet rsAlreadyChecked = null;
        ResultSet rsSelectDuplicateHashes = null;

		
		// Object to hold duplicate data
		DeDupeObj[] deDupeObj = null;
		
		// setup some counters
		int hash_count = 0;
		int loopCounter = 0;
		int duplicateCounter = 0;
        int dupe_count = 0;
		
		// setup database object
		H2 db = null;
        try {
            db = Database.getInstance();
        } catch (ConfigException e2) {
            log.error("Failed to read config file!", e2);
        }
        try {
            db.openConnection();
        } catch (ClassNotFoundException e1) {
            // means driver for database is not found
            log.fatal("Failed to read the database!", e1);
        } catch (SQLException e1) {
            log.fatal("Failed to open database!", e1);
        }
		
		// let's get to business...
		
        // initialize our prepared statements
		try {
			psCount = db.getConnection().prepareStatement(sqlCount);
			psGetHashes = db.getConnection().prepareStatement(sqlGetHashes);
			psCompare = db.getConnection().prepareStatement(sqlCompare);
			psGetRecordId = db.getConnection().prepareStatement(sqlGetRecordId);
			psInsertDupes = db.getConnection().prepareStatement(sqlInsertDupes);
			psCountBytes = db.getConnection().prepareStatement(sqlCountBytes);
            psAlreadyChecked = db.getConnection().prepareStatement(sqlAlreadyChecked);
            psSelectDuplicateHashes = db.getConnection().prepareStatement(sqlSelectDuplicateHashes);
            psSelectUnique = db.getConnection().prepareStatement(sqlSelectUnique);
		} catch (SQLException e) {
			log.error("Error setting database statements!", e);
		}
        try
        {   psSelectDuplicateHashes.execute();
            psSelectDuplicateHashes.close();

        } catch (SQLException e) {
            log.error("unable to insert duplicate hash rows!", e);
        }

        try
        {
            psSelectUnique.execute();
            psSelectUnique.close();
        } catch (SQLException e){
            log.error("unable to get unique hashes!", e);
        }
        /*
		try {
			rsCount = psCount.executeQuery();
			rsCount.next();
			// REPLACE WITH LIST<> or HASHMAP //
			hash_count = rsCount.getInt(1);
			psCount.clearParameters();
			rsCount.close();
			psCount.close();
		} catch (SQLException e) {
			log.error("Error running database queries!", e);
		}
		
		// set deDupeObj array to size of hash_count (number of hashes in database)
		deDupeObj = new DeDupeObj[hash_count];
		
		try {
		    rsGetHashes = psGetHashes.executeQuery();
		} catch (SQLException e) {
		    log.error("Error running database queries!", e);
		}
		    
		try {
			while(rsGetHashes.next()) {
				deDupeObj[loopCounter] = new DeDupeObj();
				deDupeObj[loopCounter].filehash = rsGetHashes.getString(1);
				deDupeObj[loopCounter].filepath = rsGetHashes.getString(2);
				
				loopCounter++;
			}
			rsGetHashes.close();
			psGetHashes.clearParameters();
			psGetHashes.close();
		} catch (SQLException e) {
			log.error("Error running database queries!", e);
		}
		for (int i = 0; i < deDupeObj.length; i++) {
			try {
                psAlreadyChecked.setString(1, deDupeObj[i].filehash);
                rsAlreadyChecked = psAlreadyChecked.executeQuery();
                rsAlreadyChecked.next();
                dupe_count = rsAlreadyChecked.getInt(1);
                psAlreadyChecked.clearParameters();
                rsAlreadyChecked.close();
                if (dupe_count == 0){
                    psCompare.setString(1, deDupeObj[i].filehash);
                    psCompare.setString(2, deDupeObj[i].filepath);

                    rsCompare = psCompare.executeQuery();

                    while(rsCompare != null && rsCompare.next()) {

                        psGetRecordId.setString(1, deDupeObj[i].filepath);
                        rsGetRecordId = psGetRecordId.executeQuery();
                        rsGetRecordId.next();

                        // write dupe id numbers to table
                        psInsertDupes.setLong(1, rsCompare.getLong(1));
                        psInsertDupes.setLong(2, rsGetRecordId.getLong(1));
                        psInsertDupes.setString(3, deDupeObj[i].filehash);
                        psInsertDupes.execute();

                        log.info("DUPLICATE FOUND!");//TODO add duplicates to File[] and feed into Deleter.buildGUI(File[])
                        duplicateCounter++;
                        //log.debug(deDupeObj[i].filepath + " | " + deDupeObj[i].filehash);
                       // log.debug(rsCompare.getString(2));
                        //log.debug("");
                        log.info(deDupeObj[i].filepath + " | " + rsCompare.getString(3));


				    }

                    rsCompare.close();
                    rsCompare = null;

                }
				psCompare.clearParameters();
			} catch (SQLException e) {
				log.warn("Failed to query database!", e);
				// continue running and find next dupe
				continue;
			}
		}
		try {
			psCompare.clearParameters();
			psCompare.close();
		} catch (SQLException e) {
			log.warn("Failed to close resource!", e);
		}
		long bytes = 0L;
		try {
		    rsCountBytes = psCountBytes.executeQuery();
		    if (rsCountBytes.next()) {
		        bytes = rsCountBytes.getLong(1);
		    }
		    rsCountBytes.close();
		    psCountBytes.close();
		} catch (SQLException e) {
		    log.error("Failed to get statistics!", e);
		}
		log.info("\n\n\t\t~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
		log.info("Number of Duplicates Found: " + duplicateCounter);
		log.info(" out of " + hash_count + " files");
		log.info(String.format("\nThat means %.2f%% of your files are duplicates!\n", (((double)duplicateCounter / (double)hash_count) * 100)));
		
		if (bytes >= 1073741824) { // if greater than or equal to 1GB then display in GB's
		    log.info(String.format("This accounts for about %.2f GB of wasted storage!", 
		                    ((double)bytes / (double)1073741824))); // divide by number of bytes in 1 GB
		} else {
		    log.info(String.format("This accounts for about %.2f MB of wasted storage!", 
		                    ((double)bytes / (double)1048576))); // divide by number of bytes in 1 MB
		}*/
		
		try {
            db.closeConnection();
        } catch (SQLException e) {
            log.warn("Failed to close resource!", e);
        }
	}
}
