/*********************************
 * 
 * Aragorn Self-Optimization
 * AragornDatabase.java
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

/*********************************
 * 
 * Aragorn Self-Optimization
 * AragornDatabase.java
 * Copyright(c) 2011 Kevin Croker
 * 
 * Released under GNU General Public License, GPLv3
 *
 **********************************/

package net.tightbusiness.aragorn;

import android.database.sqlite.*;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

public class AragornDatabase extends SQLiteOpenHelper {
    
    private static final String TAG = "AragornDatabase";
    private static final int DATABASE_VERSION = 6;
    
    private Resources mRes;

    public AragornDatabase(Context context) {
        super(context, "aragorn", null, DATABASE_VERSION);
	mRes = context.getResources();
    }

    @Override public void onCreate(SQLiteDatabase db) {

	db.execSQL(mRes.getText(R.string.aragorn_table_create).toString());
	db.execSQL(mRes.getText(R.string.stamps_table_create).toString());
	db.execSQL(mRes.getText(R.string.state_table_create).toString());
	db.execSQL(mRes.getText(R.string.recents_table_create).toString());
	
	Log.v(TAG, "Supposedly made the database: " + mRes);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int a, int b) {
    
	// XXX should my equalities by >= ?????
	if(a == 2 && b == 3)
	    db.execSQL(mRes.getText(R.string.recents_table_create).toString());
	if(a < 4 && b == 4)
	    db.execSQL(mRes.getText(R.string.aragorn_alter_repeat).toString());
	if(a < 5 && b >= 5)
	    db.execSQL(mRes.getText(R.string.aragorn_alter_state).toString());
	if(a < 6 && b == 6)
	    db.execSQL(mRes.getText(R.string.aragorn_alter_state2).toString());
    }
};
