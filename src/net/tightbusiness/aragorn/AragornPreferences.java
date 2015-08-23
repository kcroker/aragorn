/*********************************
 * 
 * Aragorn Self-Optimization
 * AragornPreferences.java
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
 * AragornPreferences.java
 * Copyright(c) 2011 Kevin Croker
 * 
 * Released under GNU General Public License, GPLv3
 *
 **********************************/

package net.tightbusiness.aragorn;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.ListPreference;

public class AragornPreferences extends PreferenceActivity {

    private char[] mEndeavors = null;
    private char[] mEndeavorIds = null;

    @Override protected void onCreate(Bundle savedInstanceState) {
        
	super.onCreate(savedInstanceState);

	/*	
	if(savedInstanceState != null) {
	    mEndeavors = savedInstanceState.getCharArray("endeavors");
	    mEndeavorIds = savedInstanceState.getCharArray("endeavor_ids");
	}
	else {
	    mEndeavors = getIntent().getCharArrayExtra("endeavors");
	    mEndeavorIds = getIntent().getCharArrayExtra("endeavor_ids");
	}
	*/
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override protected void onResume() {
	
	//ListPreference mIdleList = (ListPreference)getPreferenceScreen().findPreference("autotoggle_idle_preference");
	
	//mIdleList.setEntries(mEndeavors);
	//mIdleList.setEntryValues(mEndeavorIds);
	super.onResume();
    }

    @Override public void onSaveInstanceState(Bundle outState) {
	
	outState.putCharArray("endeavors", mEndeavors);
	outState.putCharArray("endeavor_ids", mEndeavorIds);
    }
};