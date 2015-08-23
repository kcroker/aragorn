/*********************************
 * 
 * Aragorn Self-Optimization
 * DbIntentService.java
 * Copyright(c) 2011 Kevin Croker
 * 
 * This file is part of Aragorn Self-Optimization, a time tracking program
 * written for the Android Platform, SDK version 8.
 *
 * Aragorn Self-Optimization is free software: you can redistribute it 
 * and/or modify it under the terms of the GNU General Public License 
 * as published by the Free Software Foundation, specifically version 3 
 * of the License.
 *
 * Aragorn Self-Optimization is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Aragorn Self-Optimization.  If not, see 
 * <http://www.gnu.org/licenses/>.
 * 
 **********************************/

package net.tightbusiness.aragorn;

import android.provider.*;

import android.database.sqlite.*;
import android.database.Cursor;

import android.app.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.content.*;
import android.view.*;
import android.util.Log;

public class DbIntentService extends IntentService {
	
    public final static String WRITE = "write";
    public final static String TAG = "DbIntentService";
    private AragornDatabase mDatabaseOpener;
    private SQLiteDatabase mDatabase;
 
    public DbIntentService() {
	super("DbIntentService");
    }

    @Override public void onDestroy() {

	super.onDestroy();
	if(mDatabase.isOpen())
	    mDatabase.close();
    }

    @Override public void onCreate() {

	super.onCreate();
	mDatabaseOpener = new AragornDatabase(this);
	mDatabase = mDatabaseOpener.getWritableDatabase();
    }

    @Override protected void onHandleIntent(Intent intent) {
 
	Bundle b = intent.getParcelableExtra("master");
	String task = intent.getStringExtra("task");
	ContentValues cv = new ContentValues();

	if(task == null) {
	    Log.w(TAG, "Received ambigious db service request");
	    return;
	}
	
	// Things dealing with stamps happen frequently, as opposed to creation or deletion of endeavors
	// so do them on the fly
	if(task.equals("reassign")) {
	    
	    cv.put("endeavor_id", intent.getStringExtra("s0"));
	    mDatabase.update("stamps", cv, "id in (SELECT id FROM stamps WHERE endeavor_id==" + intent.getStringExtra("s1") + " ORDER BY stamp DESC LIMIT " + intent.getIntExtra("limit",0) + ")", null);
	    Log.v(TAG, "wrote out reassignment");
	}
	else if(task.equals("clearall")) {
	    
	    mDatabase.delete("stamps", null, null);
	}
	else if(task.equals("clear")) {
	    
	    mDatabase.delete("stamps", "endeavor_id = " + intent.getLongExtra("mId", -1), null);
	}
    	else if(task.equals("stamp")) {
	    
	    long id = intent.getLongExtra("mId", -1);
	    if(id != -1) {
		cv.put("endeavor_id", id); 
		mDatabase.insert("stamps", null, cv);
	    }
	    Log.v(TAG, "wrote out stamp");
	}
	else if(intent.getStringExtra("task").equals("delete")) {
	    
	    long id = intent.getLongExtra("mId", -1);
	    if(id < 0)
		Log.e(TAG, "Invalid delete issued");
	    else {
		mDatabase.delete("endeavors", "id = " + id, null);
		mDatabase.delete("stamps", "endeavor_id = " + id, null);
	    }
	}
	else if(intent.getStringExtra("task").equals("update")) {
		
	    // Definitely write out to database
	    long cache = intent.getLongExtra("cache", 0);
		    
	    Log.v(TAG, "\n\tPREV: " + cache + "\n\tNOW:  " + java.lang.System.currentTimeMillis());
		    
	    Bundle eB;
	    Bundle endeavors = b.getBundle("endeavors");
	    long[] order = b.getLongArray("order");
	    int k = 0;
	    while(k < order.length) {
			
		eB = endeavors.getBundle(""+order[k]);

		cv.put("id", eB.getLong("mId"));
		cv.put("name", eB.getString("name"));
		cv.put("goal", eB.getLong("goal")); 
		cv.put("duration", eB.getLong("duration")); 
		cv.put("track", eB.getBoolean("track"));
		cv.put("repeat", eB.getBoolean("repeat"));
		cv.put("position", k++);
		cv.put("expanded", eB.getBoolean("expanded"));
			
		mDatabase.replace("endeavors", null, cv);
		Log.v(TAG, "Wrote " + eB.getString("name") + "with duration " + eB.getLong("duration"));
	    }
		    
	    // Stash the operating config
	    cv.clear();
	    cv.put("cache", b.getLong("cache"));
	    cv.put("location", b.getInt("location"));
	    cv.put("previous", b.getLong("previous"));
	    cv.put("active", b.getLong("active"));
	    cv.put("id", 0);
	    mDatabase.replace("state", null, cv);
		
	    // Refresh the recents
	    mDatabase.delete("recents", "1", null);
	    cv.clear();
	    long[] recents = b.getLongArray("recents");
	    for(k = 0; k < recents.length; ++k) {

		cv.put("id", k);
		cv.put("endeavor_id", recents[k]);
		mDatabase.replace("recents", null, cv);
	    }
	    Log.v(TAG, "Wrote out to db from IntentService");
	}
    }
};
