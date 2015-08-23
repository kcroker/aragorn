/*********************************
 * 
 * Aragorn Self-Optimization
 * EditActivity.java
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
 * EditActivity.java
 * Copyright(c) 2011 Kevin Croker
 * 
 * Released under GNU General Public License, GPLv3
 *
 **********************************/

package net.tightbusiness.aragorn;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import android.util.Log;
import android.view.View;

public class EditActivity extends Activity {
    
    public static String TAG = "EditActivity";

    NumberPicker hours, minutes, seconds;
    EditText name;
    boolean alreadyCalled;
    int mPosition;

    @Override public void onCreate(Bundle bb) {

	final Bundle b;

	alreadyCalled = false;
	requestWindowFeature(getWindow().FEATURE_NO_TITLE);
	setContentView(R.layout.edit_endeavor_popup);
	android.view.View editorView = getWindow().getDecorView();

	if(bb == null)
	    b = getIntent().getExtras();
	else
	    b = bb;

	// Note the position, we'll need it later
	mPosition = b.getInt("position");

	// Change shit
	((TextView)editorView.findViewById(R.id.title)).setText(R.string.edit_endeavor_popup);
	((ImageView)editorView.findViewById(R.id.left_icon)).setImageResource(android.R.drawable.ic_menu_edit);

	// Set the ranges because for some reason we can't do this in XML (which is retarded)
	hours = (NumberPicker)editorView.findViewById(R.id.hours);
	hours.setRange(0, 24*7);
	minutes = (NumberPicker)editorView.findViewById(R.id.minutes);
	minutes.setRange(0, 59);
	seconds = (NumberPicker)editorView.findViewById(R.id.seconds);
	seconds.setRange(0, 59);
    	
	// Populate with the relevant values
	name = (EditText)editorView.findViewById(R.id.name);
	
	if(name.getText().equals(getResources().getText(R.string.untitled_endeavor)))
	    name.setText("");
	else
	    name.setText(b.getString("name"));
	
	final CheckBox repeat = (CheckBox)editorView.findViewById(R.id.repeat);
	repeat.setChecked(b.getBoolean("repeat"));

	final int[] splits = Aragorn.millisToHMST(b.getLong("goal"));
	hours.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);
	minutes.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);
	seconds.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);
	
	hours.setCurrent(splits[0]);
	minutes.setCurrent(splits[1]);
	seconds.setCurrent(splits[2]);

	// Register the click handlers
	editorView.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {

		public void onClick(View v) {
		    Intent i = new Intent();
		    
		    i.putExtra("name", name.getText().toString());
		    i.putExtra("goal", Aragorn.hmstToMillis(hours.getCurrent(), 
							    minutes.getCurrent(), 
							    seconds.getCurrent(),
							    0));
		    i.putExtra("repeat", repeat.isChecked());
		    i.putExtra("position", mPosition);
		    setResult(RESULT_OK, i);
		    finish();
		}
	    });
	
	editorView.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
		public void onClick(View v) {
		    setResult(RESULT_CANCELED, (new Intent()).putExtras(b));
		    finish();
		}
	    });
	
	super.onCreate(b);
    }

    @Override public void onDestroy() {

	Log.v(TAG, "called onDestroy");
	super.onDestroy();
    }

    @Override public void onSaveInstanceState(Bundle outState) {

	Log.v(TAG, "saving editor instance");
	outState.putString("name", name.getText().toString());
	outState.putLong("goal", Aragorn.hmstToMillis(hours.getCurrent(), minutes.getCurrent(), seconds.getCurrent(), 0));
	outState.putInt("position", mPosition);
	alreadyCalled = true;
	
	super.onSaveInstanceState(outState);
    }
};