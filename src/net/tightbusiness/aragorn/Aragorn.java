/*********************************
 * 
 * Aragorn Self-Optimization
 * Aragorn.java
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
import android.content.res.Resources;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.io.*;
import android.util.Log;
import android.util.Pair;
import java.util.zip.*;

import android.text.format.DateFormat;
import android.widget.AdapterView.AdapterContextMenuInfo;

import android.graphics.drawable.*;

public class Aragorn extends ListActivity {

    // DEBUG
    private static final String TAG = "Aragorn";

    // Doing list adapters right
    public static int UNGOALED_ENDEAVOR = 0;
    public static int GOALED_ENDEAVOR = 1;
    public static int SEPARATOR = 2;

    final public static int ARAGORN_ACTIVITY_EDIT = 0;

    // The adapter,its ordered backing list, and the punchclock
    private EndeavorListAdapter mAdapter;
    private SeparatedListAdapter mMasterAdapter;
    private EndeavorListAdapter mRecent;

    // This list is the endeavors ordered as they are displayed to the user
    private ArrayList<Endeavor> mEndeavorDisplay;
    
    private SharedPreferences mPrefs;
    private Bundle mLatestBundle;
    private AragornReceiver mReceiver;
    private AragornDatabase mDatabaseOpener;
    private SQLiteDatabase mDatabase;
    private AlarmManager mAlarmManager;
    private LayoutInflater mInflater;
    private Resources mResources;

    private int mRepositionActive, mReassignActive;

    private boolean mAddedInstructions;

    private volatile boolean mDatabasing;
    
    public static int[] millisToHMST(long mil) {

	int[] r = new int[4];
	long s = mil / 1000;
	r[0] = (int)(s / 3600);
	r[1] = (int)((s % 3600) / 60);
	r[2] = (int)(s - r[0]*3600 - r[1]*60);
	r[3] = (int)(mil % 1000) / 100;
	return r;
    }
 
    public static long hmstToMillis(int h, int m, int s, int t) {

	return (s + m*60 + h*3600)*1000 + t*100;
    }

    public static String formatTime(long t) {

	return formatTime(t, false);
    }
    public static String formatTime(long t, boolean hires) {

	int[] splits = millisToHMST(t);
	if(hires)
	    return String.format("%02d:%02d:%02d.%1d", splits[0], splits[1], splits[2], splits[3]);  
	else
	    return String.format("%02d:%02d:%02d", splits[0], splits[1], splits[2]);  
    }

    private static String renderStamps(Endeavor e) {

	StringBuffer dicks = new StringBuffer();
	Iterator<Date> i = e.getStamps().iterator();
	java.lang.CharSequence pS, cS;
	Date current, previous;
	boolean end;

	pS = null;
	end = true;
	previous = null;

	if(i.hasNext()) {
		
	    previous = i.next();
	    pS = DateFormat.format("MM/dd/yy", previous);
	    dicks.append(pS.toString() + "\n\t" + DateFormat.format("hh:mmaa", previous).toString() + " - ");
	}
	
	while(i.hasNext()) {

	    current = i.next();
	    cS = DateFormat.format("MM/dd/yy", current);
		
	    // String comparison, not object
	    if(cS.equals(pS)) {
		    
		// Then we are still on the same day, output this stamp as one deal
		// Are we even or odd?
		if(end) {
			
		    dicks.append(DateFormat.format("hh:mmaa", current).toString() + "\t" + formatTime(current.getTime() - previous.getTime(), false) + "\n");
		    end = false;
		}
		else {
		    dicks.append("\t" + DateFormat.format("hh:mmaa", current).toString() + " - ");
		    end = true;
		}
	    }
	    else {
		
		if(end) {
		    dicks.append("\n" + cS.toString() + "\n\t               - " + DateFormat.format("hh:mmaa", current).toString() + "\t" + formatTime(current.getTime() - previous.getTime(), false) + "\n");
		    end = false;
		}
		else {
		    
		    dicks.append("\n" + cS.toString() + "\n\t" + DateFormat.format("hh:mmaa", current).toString() + " - ");
		    end = true;
		}
	    }
	    previous = current;
	    pS = cS;
	}
	
	return dicks.toString();
    }

    public void export() {

	// This will be slow, thread the entire thing, including the firing of the intent!
	(new Runnable() {
		public void run() {

		    // Produce a temporary zip file, overwriting it if it already exists
		    String packageName = getPackageName();
		    File externalPath = android.os.Environment.getExternalStorageDirectory();
		    File base = new File(externalPath.getAbsolutePath() +
					 "/Android/data/" + packageName + "/files");
		    File zip = new File(base, "Aragorn Tracked.zip");
		    File trackingBase = new File(base, "tracking/");

		    // Workaround for API level 7
		    
		    try {
			ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)));

			// List the directory, write all files
			File[] list = trackingBase.listFiles();
			if(list == null)
			    return;

			for (int i = 0; i < list.length; ++i) {

			    Log.v(TAG, "\tadding " + list[i].getName() + " to ZIP file...");
			    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(list[i].getAbsolutePath()));
			    ZipEntry entry = new ZipEntry(list[i].getName());
			    zos.putNextEntry(entry);
			    
			    byte[] b = new byte[(int)list[i].length()];
			    bis.read(b, 0, (int)list[i].length());
			    zos.write(b, 0, (int)list[i].length());
			    zos.closeEntry();
			    Log.v(TAG, "\twrote " + list[i].length() + " byten");
			    bis.close();
			}
			zos.close();
		    } 
		    catch(IOException barf) {

			barf.printStackTrace();
		    }

		    // Now send away! (super cool)
		    Intent sendIntent = new Intent(Intent.ACTION_SEND);
		    sendIntent.setType("application/zip");
		    sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Aragorn Tracked Time");
		    sendIntent.putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(zip));
		    Log.v(TAG, "\tAttempting to intent " + android.net.Uri.fromFile(zip));

		    try {
			startActivity(Intent.createChooser(sendIntent, "Send tracked data (zip)"));
		    } catch (android.content.ActivityNotFoundException ex) {
			// (handle error)
		    }
		}
	    }).run();
    }

    public void trackEndeavors() {

	
	if(!android.os.Environment.MEDIA_MOUNTED.equals(android.os.Environment.getExternalStorageState())) {

	    toasty("Cannot access SD card, tracking aborted");
	    return;
	}
	
	// Establish the tracking dir so I don't have to do it many times
	// Workback for Android API 7
       	// Should probably do this once, elsewhere...
	String packageName = getPackageName();
	File externalPath = android.os.Environment.getExternalStorageDirectory();
	final File trackingBase = new File(externalPath.getAbsolutePath() +
				     "/Android/data/" + packageName + "/files/tracking");
	
	// The last 100m.  Hit the preferences hard, lets do this
	Iterator<Endeavor> i = mEndeavorDisplay.iterator();
	final ArrayList<Pair<String,String>> stampSet = new ArrayList<Pair<String,String>>();
	
	Endeavor e;
	long freeze = (new Date()).getTime();
	long timestamp = freeze;
	boolean useIds = mPrefs.getBoolean("prefs_export_filename", false);
	boolean nameColumn = mPrefs.getBoolean("prefs_export_namecolumn", false);

	// Freeze so that our stats reflect the moment
	// tracking was requested
	mAdapter.suspend();

	while(i.hasNext()) {

	    e = i.next();
	    if(!e.isTracked())
		continue;
	    
	    // Okay, this one is tracked, determine if it has a least timestamp
	    // stamps are monotonic within each array, so only look at first element
	    ArrayList<Date> d = e.getStamps();
	    if(!d.isEmpty())
		timestamp = (d.get(0).getTime() < timestamp) ? d.get(0).getTime() : timestamp;
	    
	    StringBuffer stamp = new StringBuffer();

	    // endeavor time \t goal time 
	    stamp.append(""+e.getDuration()+"\t"+e.getGoal()+"\n");

	    // put the optional column at the end so to allow analysis code to run on either style
	    // set without modification!
	    if(nameColumn)
		stamp.append(e.getName()+"\t");
	   	   
	    if(useIds)
		stampSet.add(new Pair(""+e.getId()+".dat", stamp.toString()));
	    else
		stampSet.add(new Pair(e.getName()+".dat", stamp.toString()));
	}
	mAdapter.resume();
	
	// Must be final because we need it in the write thread
	final long usestamp;

	// Should we not use the earliest?
	if(mPrefs.getBoolean("prefs_export_timepoint", false))
	    usestamp = freeze;
	else
	    usestamp = timestamp;

	// Thread out for the slow stuff (writes)
	(new Runnable() {
		
		public void run() {

		    Iterator<Pair<String, String>> j = stampSet.iterator();
		    Pair<String, String> p;

		    while(j.hasNext()) {

			p = j.next();

			if(!trackingBase.exists()) {
			    
			    // Make the directory and populate it with a readme file so people
			    // can make sense of the table columns
			    trackingBase.mkdir();
			    
			    File readme = new File(trackingBase, "README");
			    
			    try {
				BufferedWriter os = new BufferedWriter(new FileWriter(readme));
				String stamp = getString(R.string.tracking_readme);
				os.write(stamp, 0, stamp.length());
				os.close();
			    } catch (IOException e) {
				// Unable to create file, likely because external storage is
				// not currently mounted.
				Log.w("ExternalStorage", "Error writing " + readme, e);
			    }
			}
			
			File file = new File(trackingBase, p.first);

			try {
			    BufferedWriter os = new BufferedWriter(new FileWriter(file, true));
			    String stamp = "" + usestamp + "\t" + p.second;
			    os.write(stamp, 0, stamp.length());
			    os.close();
			} catch (IOException e) {
			    // Unable to create file, likely because external storage is
			    // not currently mounted.
			    Log.w("ExternalStorage", "Error writing " + file, e);
			}
		    }

		    // stamp format: 
		    // Should we do a globalstats file?
		    if(mPrefs.getBoolean("prefs_export_globalstats", true)) {
			
			File file = new File(trackingBase, "global.dat");

			try {
			    BufferedWriter os = new BufferedWriter(new FileWriter(file, true));
			    int[] cums = cumulativeStats();
			    String stamp = "" + usestamp + "\t" + cums[0] + "\t" + cums[1] + "\t" + cums[2] + "\n";
			    os.write(stamp, 0, stamp.length());
			    os.close();
			} catch (IOException ee) {
			    // Unable to create file, likely because external storage is
			    // not currently mounted.
			    Log.w("ExternalStorage", "Error writing " + file, ee);
			}
		    }
		}
	    }).run();
	
	// Just to give the user some feedback
	toasty(mResources.getString(R.string.writingOut));

    }


    private void reset() {
	 
	Iterator i = mEndeavorDisplay.iterator();
	Endeavor e;
	
	mAdapter.disable();
			    
	while(i.hasNext()) {
	    
	    e = (Endeavor)i.next();
	    e.setDuration(0);
	    e.clearStamps();
	}

	Intent writeIntent = new Intent(this, DbIntentService.class);
	writeIntent.putExtra("task", "clearall");
	startService(writeIntent);

	mAdapter.notifyDataSetChanged();
	updateGlobalStats();
    }
	   

    public void resetAllEndeavors() {

	if(mPrefs.getBoolean("verify_reset_preference", true)) {
	    // Reset all accumulated times to zero
	    new AlertDialog.Builder(Aragorn.this)
		.setIcon(android.R.drawable.ic_menu_revert)
		.setTitle(R.string.reset_verification)
		.setPositiveButton(mResources.getString(R.string.yes), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			    reset();
			}
		    })
		.setNegativeButton(mResources.getString(R.string.no), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			    toasty(mResources.getString(R.string.resetAborted)); 
			}
		    })
		.create().show();
	}
	else
	    reset();
    }

    public void editEndeavor(int position) {

	editEndeavor(position, false);
    }

    public void editEndeavor(int position, boolean isNew) {

	Intent i = new Intent(this, EditActivity.class);
	Endeavor e = mAdapter.getItem(position);

	i.putExtra("name", e.getName());
	i.putExtra("goal", e.getGoal());
	i.putExtra("repeat", e.isRepeated());
	i.putExtra("position", position);
	i.putExtra("new", isNew);
	startActivityForResult(i, ARAGORN_ACTIVITY_EDIT); 
    }

    public void addEndeavor() {
	
	mEndeavorDisplay.add(new Endeavor(""+(Endeavor.uniqueId+1) /*getResources().getText(R.string.untitled_endeavor).toString()*/, 0, 0, false));
	mAdapter.notifyDataSetChanged();

	// Scroll to the position (always added at the bottom)
	int pos = mEndeavorDisplay.size()-1;
	getListView().setSelection(pos + mAdapter.offsetAdjustment());

	// Fire an immediate intent to edit it
	editEndeavor(pos + mAdapter.offsetAdjustment(), true);
		     
    }
    private void delete(int position) {

	// Don't bother stamping, we're deleting anyway
	final Endeavor e = mAdapter.getItem(position);

	if(e.equals(mAdapter.getRunningEndeavor()))
	    mAdapter.disable(false);
	    
	// Remove from the display list
	Log.v(TAG, "delete: pos " + position + ", adjust: " + mAdapter.offsetAdjustment());
	mEndeavorDisplay.remove(e);
	mAdapter.flushRecents(e);

	// Db code only updates existing entries, does not rewrite/delete them!
	Intent writeIntent = new Intent(this, DbIntentService.class);
	writeIntent.putExtra("task", "delete");
	writeIntent.putExtra("mId",e.getId()); 
	startService(writeIntent);

	// Update the list...
	mAdapter.notifyDataSetChanged();
	updateGlobalStats();
    }

    public void deleteEndeavor(final int position) {

	// Should we verify?
	if(mPrefs.getBoolean("verify_del_preference", true)) {
	    
	    new AlertDialog.Builder(Aragorn.this)
		.setIcon(android.R.drawable.ic_menu_delete)
		.setTitle(mResources.getString(R.string.delete) + " " + mAdapter.getItem(position).getName() + mResources.getString(R.string.questionMark))
		.setPositiveButton(mResources.getString(R.string.yes), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			    delete(position);
			}
		    })
		.setNegativeButton(mResources.getString(R.string.no), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
		
			    toasty(mResources.getString(R.string.deleteAborted));
			}
		    })
		.create().show();
	}
	else 
	    delete(position);
	    
    }
    
    public void clearEndeavor(final Endeavor e) {
	
	if(e.equals(mAdapter.getRunningEndeavor()))
	    mAdapter.disable();
	e.setDuration(0);
	e.clearStamps();
	
	Intent writeIntent = new Intent(this, DbIntentService.class);
	writeIntent.putExtra("task", "clear");
	writeIntent.putExtra("mId", e.getId());
	startService(writeIntent);

	updateGlobalStats();
	mAdapter.notifyDataSetChanged();
    }

    public void toasty(String text) {

	toasty(text, Toast.LENGTH_SHORT);
    }


    public void toasty(String text, int duration) {

	Toast toast = Toast.makeText(this, text, duration);
	toast.show();

    }

    public int[] cumulativeStats() {

	// 0 - sum durations (ungoaled)
	// ---- now only non-repeating endeavors ----
	// 1 - sum durations (goaled)
	// 2 - sum goals
	// 3 - sum durations (truncated goaled)

	long dGoals = 0, dUngoaled = 0, goals = 0;
	long goal, dur;

	try {
	    Iterator<Endeavor> i = mEndeavorDisplay.iterator();
	    int[] stats = new int[4];
	    Endeavor e;

	    while(i.hasNext()) {

		e = i.next();
		goal = e.getGoal();
		dur = e.getDuration();

		if(goal > 0 && !e.isRepeated()) {
		    stats[2] += goal;
		    stats[1] += dur;
		    stats[3] += dur > goal ? goal : dur;
		}
		else
		    stats[0] += dur;
	    }
	
	    return stats;
	} catch(ConcurrentModificationException e) {

	    // That's fine, just don't update stats this round
	    return null;
	}

    }

    public void updateGlobalStats() {
	
	int[] stats = cumulativeStats();
	if(stats != null) {
	    
	    String name = getResources().getText(R.string.app_name).toString();
	    String version = getResources().getText(R.string.app_version_name).toString();
	    Endeavor e = mAdapter.getRunningEndeavor();
	    String desc = new String();
	    String details = new String();
	    
	    setTitle("v" + version + " - " + formatTime(stats[0] + stats[1]) + " - " + formatTime(stats[3]) + " / " + formatTime(stats[2]));
	    setProgress((int) (((double)stats[3]/(double)stats[2])*9999));
	}
    }

    public Endeavor findEndeavorById(long id) {

	Iterator<Endeavor> i = mEndeavorDisplay.iterator();
	Endeavor e;
	
	while(i.hasNext()) {
	    
	    e = i.next();
	    if(e.getId() == id)
		return e;
	}
	return null;
    }

    @Override public void onSaveInstanceState(Bundle outState) {
	
	super.onSaveInstanceState(outState);
	Log.v(TAG, "saving instance");
	
	outState.putBundle("master", emitBundle());
    }

    //
    // Save everything in the bundle
    //
    public Bundle emitBundle() {

	Bundle masterBundle = new Bundle();
	Bundle endeavorBundle = new Bundle();
	
	Iterator<Endeavor> i = mEndeavorDisplay.iterator();
	Endeavor e;
	long[] order = new long[mEndeavorDisplay.size()];
	int k = 0;
	
	// Build the endeavor bundle
	while(i.hasNext()) {
	    
	    e = i.next();
	    endeavorBundle.putBundle(""+e.getId(), e.emitBundle());
	    order[k++] = e.getId();
	}
	
	// Stash it in the master bundle
	masterBundle.putBundle("endeavors", endeavorBundle);
	masterBundle.putLongArray("order", order);

	// Add the recents
	masterBundle.putLongArray("recents", mAdapter.getRecents());
	
	masterBundle.putLong("cache", mAdapter.getLastFire());
	masterBundle.putInt("location", getListView().getFirstVisiblePosition());
	masterBundle.putLong("active", mAdapter.getRunningEndeavor() != null ? mAdapter.getRunningEndeavor().getId() : -1);
	masterBundle.putLong("previous", mAdapter.getPreviousEndeavor() != null ? mAdapter.getPreviousEndeavor().getId() : -1);
	return masterBundle;
    }

    /** Called when the activity is first created. */
    @Override public void onCreate(Bundle savedInstanceState) {
	
	// Super call
	super.onCreate(savedInstanceState);

	Log.v(TAG, "Entered onCreate");
	
	// Before we do anything else
	requestWindowFeature(Window.FEATURE_PROGRESS);

	// Load preferences
	//	android.preference.PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
	mPrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);

	// Set keyguard stuff
	if(mPrefs.getBoolean("keyguard_preference", false))
	    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
	
	// Get an inflater once, because we'll need it a few times
	mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	
	// Initialize the display list
	mEndeavorDisplay = new ArrayList<Endeavor>();

	// Open a persistent connection to the database
	mDatabaseOpener = new AragornDatabase(this);
	mDatabase = mDatabaseOpener.getWritableDatabase();
	mAlarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);

	// Create the adapter, so we can keep track of the running 
	// Also create the master!
	mAdapter = new EndeavorListAdapter(this, mEndeavorDisplay);
	
	int prevDisplay;

	// TRY TO USE BUNDLES
	if(savedInstanceState != null && savedInstanceState.getBundle("master") != null) {

	    Log.v(TAG, "Loading from bundle!!");
	    Bundle master = savedInstanceState.getBundle("master");
	    
	    // We can restore from the bundle, first load Endeavors
	    // must do silly things to extract the individual endeavors
	    Bundle endeavors = master.getBundle("endeavors");
	    long[] ids = master.getLongArray("order");
	    long activeId = master.getLong("active");
	    long prevId = master.getLong("previous");
	    prevDisplay = master.getInt("location");
	    int k = 0;
	    Endeavor e;
	    long stiz;

	    while(k < ids.length) {
		
		e = new Endeavor(endeavors.getBundle(""+ids[k++]));
		stiz = e.getId();

		if(stiz == activeId)
		    mAdapter.setRunningEndeavor(e);
		else if(stiz == prevId)
		    mAdapter.setPreviousEndeavor(e);
		
		// Restore the id count if we successfully load from bundle!
		if(Endeavor.uniqueId < stiz)
		    Endeavor.uniqueId = stiz+1;

		mEndeavorDisplay.add(e);
	    }
		
	    // Then restore recents
	    mAdapter.restoreRecents(master.getLongArray("recents"));
		
	    // Restore cached time
	    mAdapter.setPrevTime(master.getLong("cache"));

	    // For sharing
	    mLatestBundle = savedInstanceState;
	}
	else {
	    
	    // Load the recents list
	    Cursor c = mDatabase.query("recents", null, null, null, null, null, "id ASC");
	    c.moveToFirst();
	    long[] recents = new long[c.getCount()];
	    int k = 0;
	    while(!c.isAfterLast()) {
	 
		recents[k++] = c.getLong(c.getColumnIndexOrThrow("endeavor_id"));
		c.moveToNext();
	    }
	    c.close();

	    // To keep time correct, we must repull from the database.
	    String[] homie = new String[1];
	    long prevId, activeId;

	    homie[0] = "cache,previous,active,location";
	    c = mDatabase.query("state", homie, "id=0", null, null, null, null);
	    c.moveToFirst();
	    if(!c.isAfterLast()) {
		mAdapter.setPrevTime(c.getLong(c.getColumnIndex("cache")));
		prevId = c.getLong(c.getColumnIndex("previous"));
		activeId = c.getLong(c.getColumnIndex("active"));
		prevDisplay = c.getInt(c.getColumnIndex("location"));
	    }
	    else {
		mAdapter.setPrevTime(0);
		prevId = -1;
		activeId = -1;
		prevDisplay = 0;
	    }
	    c.close();
	    
	    // Load the endeavors, honouring list order
	    String[] s = new String[1];
	    c = mDatabase.query("endeavors", null, null, null, null, null, "position ASC"); 
	
	    c.moveToFirst();
	    while(!c.isAfterLast()) {

		Endeavor e = new Endeavor();
		long cId = c.getLong(c.getColumnIndexOrThrow("id"));
	    
		//  Set the id and string it for the stamp query
		s[0] = "" + e.setId(cId);
		if(cId > Endeavor.uniqueId)
		    Endeavor.uniqueId = cId;
	    
		// Restore the timestamps
		Cursor stamps = mDatabase.rawQuery(getResources().getText(R.string.db_fetch_stamps).toString(),s);
		stamps.moveToFirst();
		while(!stamps.isAfterLast()) {
		
		    e.stamp(new Date(stamps.getLong(stamps.getColumnIndex("stamp"))));
		    stamps.moveToNext();		
		}
		stamps.close();
	    
		// Restore the member properties
		e.setName(c.getString(c.getColumnIndex("name")));
		long dur = c.getLong(c.getColumnIndex("duration"));
		Log.v(TAG, "Duration in read from db for endeavor " + e.getName() + ": " + formatTime(dur));
		e.setDuration(c.getLong(c.getColumnIndex("duration")));
		e.setTracked(c.getInt(c.getColumnIndex("track")) > 0 ? true : false);
		e.setGoal(c.getLong(c.getColumnIndex("goal")));
		e.setRepeated(c.getInt(c.getColumnIndex("repeat")) > 0 ? true : false);

		// UI shit...because we now ordered by position ascending, we can just add as we encounter
		e.expanded(c.getInt(c.getColumnIndex("expanded")) > 0 ? true : false);
	
		// Restore state
		if(e.getId() == activeId)
		    mAdapter.setRunningEndeavor(e);
		if(e.getId() == prevId)
		    mAdapter.setPreviousEndeavor(e);

		mEndeavorDisplay.add(e);
		c.moveToNext();
	    }
	    c.close();
	    
	    // Restore the recents, **stupid** slow shit, O(n*m)
	    mAdapter.restoreRecents(recents);
	}

	// For speed
	mResources = getResources();

	// Add defaults if its empty
	if(mEndeavorDisplay.size() == 0) {
	    
	    // A rig job, but whatever
	    mEndeavorDisplay.add(new Endeavor(mResources.getString(R.string.default1), 7200, 0, true));
	    mEndeavorDisplay.add(new Endeavor(mResources.getString(R.string.default2), 0, 0, false));
	    mEndeavorDisplay.add(new Endeavor(mResources.getString(R.string.default3), 0, 0, true));
	    mEndeavorDisplay.add(new Endeavor(mResources.getString(R.string.default4), 0, 0, false));
	    mEndeavorDisplay.add(new Endeavor(mResources.getString(R.string.default5), 0, 0, false));
	 
	    // There was nothing here
	    toasty(mResources.getString(R.string.defaultsCreated), Toast.LENGTH_LONG);
	}

	// Bind our view to the adapter
	setListAdapter(mAdapter);
	registerForContextMenu(getListView());
	
	// (re)set the right position
	getListView().setSelection(prevDisplay);

	// Catch screen on/screen off events
	IntentFilter fi = new IntentFilter(Intent.ACTION_SCREEN_ON);
	fi.addAction(Intent.ACTION_SCREEN_OFF);
	fi.addAction("aragorn.intent.action.NOTIFY");
	mReceiver = new AragornReceiver();
	registerReceiver(mReceiver, fi);

	// Define what WE mean by airplane mode
	Settings.System.putString(getContentResolver(), Settings.System.AIRPLANE_MODE_RADIOS, 
				  Settings.System.RADIO_BLUETOOTH + "," + 
				  Settings.System.RADIO_CELL + "," + 
				  Settings.System.RADIO_WIFI);

	// Signal that we need to add instructions again because we just recreated
	// everything
	mAddedInstructions = false;
    }
    
    @Override public void onResume() {
	
	super.onResume();
	
	Endeavor e;
	Log.v(TAG, "Entered onResume");

	setProgressBarVisibility(mPrefs.getBoolean("prefs_totalprogress", false));

	// If there was a reposition or transfer in progress, nullify it
	mRepositionActive = -1;
	mReassignActive = -1;

    }
    
    @Override public void onPause() {
	
	super.onPause();
	Log.v(TAG, "Entered onPause");

	Intent writeIntent = new Intent(this, DbIntentService.class);
	mAdapter.suspend();
	writeIntent.putExtra("master", emitBundle());
	writeIntent.putExtra("task", "update");
	startService(writeIntent);
	mAdapter.resume();
    }
    
    @Override public void onRestart() {

	super.onRestart();
	Log.v(TAG, "Entered onRestart");
    }

    @Override public void onStop() {

	super.onStop();
	Log.v(TAG, "Entered onStop");
	
	// Freeze the timer until onStart()
	mAdapter.suspend();
    }

    @Override public void onStart() {
	
	super.onStart();
	Endeavor e;
	Log.v(TAG, "Entering onStart");

	// Now, if nothing is running, then any previously cached time is crap
	if(mAdapter.getRunningEndeavor() == null)
	    mAdapter.setPrevTime(0);

	// Start back up
	mAdapter.resume();
    }

    @Override public void onDestroy() {

	super.onDestroy();
	Log.v(TAG, "Entered onDestroy");
	unregisterReceiver(mReceiver);
	if(mDatabase.isOpen())
	    mDatabase.close();
    }

    // Establish the root menu
    @Override public boolean onCreateOptionsMenu(Menu menu) {
	
	MenuInflater inflater = getMenuInflater();
	inflater.inflate(R.menu.aragorn_menu, menu);
	return true;
    }

    @Override public void onBackPressed() {

	moveTaskToBack(true);
     }

    // Establish the context menu
    // This might have to go on each list entry...
    @Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

	super.onCreateContextMenu(menu, v, menuInfo);
	MenuInflater inflater = getMenuInflater();

	inflater.inflate(R.menu.list_float_menu, menu);

	// Make per item adjustments to prevent wonky behaviour
	if(((AdapterView.AdapterContextMenuInfo)menuInfo).position - mAdapter.offsetAdjustment() < 0)
	    menu.removeItem(R.id.reposition);
	
	if(mAdapter.getItem(((AdapterView.AdapterContextMenuInfo)menuInfo).position).isRepeated())
	    menu.removeItem(R.id.transfer);
    }
    
    // Handle the root menu stuff
    @Override public boolean onOptionsItemSelected(MenuItem item) {
	
	switch (item.getItemId()) {
	    
	case R.id.add:
	    addEndeavor();
	    return true;
	case R.id.options:
	    // Tell the preferences screen what the current idle options are
	    String[] es = new String[mEndeavorDisplay.size()];
	    String[] ids = new String[mEndeavorDisplay.size()];
	    Endeavor e;
	    
	    Iterator<Endeavor> i = mEndeavorDisplay.iterator();
	    
	    int k = 0;
	    while(i.hasNext()) {
		e = i.next();
		es[k] = e.getName();
		ids[k++] = ""+e.getId();
	    }

	    Intent action = new Intent(this, AragornPreferences.class);
	    action.putExtra("endeavors", es);
	    action.putExtra("endeavor_ids", ids);
	    startActivity(action);
	    return true;
	case R.id.track:
	    trackEndeavors();
	    return true;
	case R.id.pause:
	    toasty(mResources.getString(R.string.neverPause));
	    return true;
	case R.id.reset:
	    resetAllEndeavors();
	    return true;
	case R.id.export:
	    export();
	    return true;

	default:
	    return super.onOptionsItemSelected(item);
	}
    }

    // Handle the context menu
    @Override public boolean onContextItemSelected(MenuItem item) {
	
	final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	Endeavor e;

	switch (item.getItemId()) {
	
	case R.id.reposition:
	    toasty(mResources.getString(R.string.repositionSelect));
	    mRepositionActive = info.position;
	    return true;
	case R.id.transfer:
	    if(mAdapter.getItem(info.position).getStamps().size() == 0)
		toasty(mResources.getString(R.string.transferError1));
	    else {
		toasty(mResources.getString(R.string.transferSelect));
		mReassignActive = info.position;
	    }
	    return true;
	case R.id.graph:
	    toasty(mResources.getString(R.string.notImplemented));
	    return true;
	case R.id.clear:
	    clearEndeavor(mAdapter.getItem(info.position));
	    return true;
	case R.id.edit:
	    editEndeavor(info.position);
	    return true;
	case R.id.delete:
	    deleteEndeavor(info.position);
	    return true;
	default:
	    return super.onContextItemSelected(item);
	}
    }

    public long performReassignment(Endeavor recv, Endeavor send) {

	long now = mAdapter.getLastFire();
	long delta;

	// Determine if we need to xfer two stamps or just one
	// If the sender is presently running, xfer only one, and set the receiver to running
	// If not, xfer two
	if(send.equals(mAdapter.getRunningEndeavor())) {
		    
	    // Transfer the most recent stamp
	    ArrayList<Date> stamps = send.getStamps();
	    Date recent = stamps.remove(stamps.size()-1);
	    recv.getStamps().add(recent);

	    // Reassign the stamp in the db to this endeavor.  Order doesn't matter because
	    // I order by date ascending when I fetch them!
	    final String[] s = new String[2];
	    s[0] = ""+recv.getId();
	    s[1] = ""+send.getId();
	    
	    Intent writeIntent = new Intent(this, DbIntentService.class);
	    writeIntent.putExtra("task", "reassign");
	    writeIntent.putExtra("s0", s[0]);
	    writeIntent.putExtra("s1", s[1]);
	    writeIntent.putExtra("limit", 1);
	    startService(writeIntent);

	    // Modify the durations accordingly
	    delta = now - recent.getTime();
	    send.increment(-delta);
	    recv.increment(delta);
	}
	else {
		    		    
	    ArrayList<Date> stamps = send.getStamps();
	    Date t2 = stamps.remove(stamps.size()-1);
	    Date t1 = stamps.remove(stamps.size()-1);
	    delta = t2.getTime() - t1.getTime();
		    
	    send.increment(-delta);
	    recv.increment(delta);
	    stamps = recv.getStamps();
		  
	    // Update the db, the most recent two belonging to the sender
	    final String[] s = new String[2];
	    s[0] = ""+recv.getId();
	    s[1] = ""+send.getId();
	    
	    Intent writeIntent = new Intent(this, DbIntentService.class);
	    writeIntent.putExtra("task", "reassign");
	    writeIntent.putExtra("s0", s[0]);
	    writeIntent.putExtra("s1", s[1]);
	    writeIntent.putExtra("limit", 2);
	    startService(writeIntent);

	    // Search through the stamps until we find where to insert
	    Iterator<Date> i = stamps.iterator();
	    int k = 0;
	    while(i.hasNext()) {
			
		// remember, ArrayList.add() inserts before the element already there
		if(i.next().getTime() >= t2.getTime())
		    break;
		++k;
	    }
		    
	    // Equalities should be merged, two different cases.  Meh, do later
	    // Now add at k
	    stamps.add(k, t2);
	    // add at k again
	    stamps.add(k, t1);
	    

	    // If the receiving was goaled and running, reset alarms
	    if(recv.equals(mAdapter.getRunningEndeavor()) && recv.getGoal() > 0) {
			
		killPendingAlarm(recv);
	 	mAdapter.registerDNDAndAlarm(true);
	    }
	}

	return delta;
    }

    public void registerPendingAlarm(Endeavor e) {
		    
	// Try just using one deal...
	Intent i = new Intent("aragorn.intent.action.NOTIFY");
	PendingIntent pending;
	try {
	i.setClass(this, Class.forName("AragornReceiver"));
	} catch(Exception ex) {
	    ex.printStackTrace();
	}

	i.putExtra("name", e.getName().toString());
	i.putExtra("id", e.getId());
	i.putExtra("repeat", e.isRepeated());
	i.setFlags(0); //Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP); //Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);	
	pending = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
	    
	long when;
	if(e.isRepeated()) {
	 
	    when = e.getGoal() - (java.lang.System.currentTimeMillis() - e.lastStamp().getTime()) % e.getGoal();
	    if(when == 0)
		when = e.getGoal();
	}
	else
	    when = e.getGoal() - e.getDuration();
	
	// Only fire if we need to
	if(when > 0)
	    mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
			      android.os.SystemClock.elapsedRealtime() + when, 
			      pending); 
    }
	
    public void killPendingAlarm(Endeavor e) {

	Intent i = new Intent("aragorn.intent.action.NOTIFY");
	PendingIntent pending;
	
	try{
	i.setClass(this, Class.forName("AragornReceiver"));
	} catch(Exception ex) {

	    ex.printStackTrace();
	}
	i.putExtra("name", e.getName().toString());
	i.putExtra("id", e.getId());
	i.setFlags(0); //Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);	

	pending = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
	pending.cancel();
    }

    // Handle collapse/expansion
    @Override protected void onListItemClick(ListView l, View v, int position, long id) {    
	
	// Perform a reposition
	if(mRepositionActive > -1) {

	    if(mRepositionActive == position)
		toasty(mResources.getString(R.string.repositionCancel));
	    else if(position - mAdapter.offsetAdjustment() < 0) 
		toasty(mResources.getString(R.string.repositionError1));
	    else {
		
		// mRepositionActive corresponds to a position on the screen, not within the list!
		Endeavor e = mEndeavorDisplay.remove(mRepositionActive - mAdapter.offsetAdjustment());
		mEndeavorDisplay.add(position - mAdapter.offsetAdjustment(), e);
		mAdapter.notifyDataSetChanged();
	    }

	    // End reposition
	    mRepositionActive = -1;
	}
	else if(mReassignActive > -1) {

	    Endeavor recv = mAdapter.getItem(position);
	    Endeavor send = mAdapter.getItem(mReassignActive);
	    Endeavor prevActive = mAdapter.getRunningEndeavor();
	    long delta;

	    if(recv.equals(send))
		toasty(mResources.getString(R.string.transferCancel));
	    else if(recv.isRepeated())
		toasty(mResources.getString(R.string.transferError2));
	    else {
		
		// Freeze time
		mAdapter.suspend();
		delta = performReassignment(recv, send);
		toasty(String.format(mResources.getString(R.string.transferSuccess), formatTime(delta)));

		// Set the running endeavor
		// (set running endeavor should probably do this because all these things
		// seem to be associated with specifically setting the running endeavors)
		if(send == prevActive) {
		    mAdapter.disable(false);
		    mAdapter.setRunningEndeavor(recv);
		    mAdapter.setPreviousEndeavor(send);
		    mAdapter.pushRecents(recv);
		    mAdapter.registerDNDAndAlarm(send.getGoal() > 0);
		}
		mAdapter.notifyDataSetChanged();
		mAdapter.resume();
	    }

	    // End reassign
	    mReassignActive = -1;
	}
	else {
	    
	    // Use magic to determine what view was beneath me
	    View clicked = v.findFocus();
	    
	    //    toasty(""+clicked);
	    mAdapter.getItem(position).expanded(!mAdapter.getItem(position).expanded());
	    mAdapter.notifyDataSetChanged();
	}
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
     
	super.onActivityResult(requestCode, resultCode, intent);

	// If we aborted things, bail out
      	if(intent == null)
	    return;

	Bundle extras = intent.getExtras();
	switch(requestCode) {

	case ARAGORN_ACTIVITY_EDIT:
	    switch(resultCode) {
	    case RESULT_OK:
		Endeavor e = mAdapter.getItem(extras.getInt("position"));
		
		if(extras.getLong("goal") == 0 && extras.getBoolean("repeat")) {
		    toasty(mResources.getString(R.string.editErrorNoPeriod));
		   break;
		}

		mAdapter.suspend();

		e.setName(extras.getString("name"));     
		e.setGoal(extras.getLong("goal")); 
		e.setRepeated(extras.getBoolean("repeat"));

		long oldgoal = e.getGoal();
		// If we editted the running one, we might need to cancel alarms, register new ones
		// do airplane mode, etc
		if(e.equals(mAdapter.getRunningEndeavor())) {
		    if(oldgoal > 0 && e.getGoal() == 0)
			killPendingAlarm(e);
		    mAdapter.registerDNDAndAlarm(oldgoal > 0);
		}
		mAdapter.notifyDataSetChanged();
		updateGlobalStats();
		mAdapter.resume();
		break;
	    case RESULT_CANCELED:
		// See if this was a new endeavor, if so, delete it
		if(extras.getBoolean("new"))
		    delete(extras.getInt("position"));
		else 
		    toasty(mResources.getString(R.string.editCancel));
		break;
	    }
	    break;
	default:
	    break;
	}
    }
    public class AragornReceiver extends BroadcastReceiver {

	//
	// This fucker runs regardless of whatever state the app is in
	// moreso, it runs in a separate thread.  Do not start things 
	// from here, lest you suck dicks and die
	//
	private Endeavor mCachedEndeavor;
	
	public AragornReceiver() {

	    super();
	    mCachedEndeavor = null;
	}

	public void onReceive(Context context, Intent intent) {
	
	    String action = intent.getAction();
	    
	    // This one is fine as it makes no reference to running things
	    if(mPrefs.getBoolean("alarm_preference", true) && 
	       action.equals("aragorn.intent.action.NOTIFY")) {
		    
		// Wake up and bling the phone
		String name = intent.getStringExtra("name");
		Boolean repeated = intent.getBooleanExtra("repeat", false);
		Notification notification;
		CharSequence contentTitle, contentText;

		if(!repeated) {

		    contentTitle = String.format(mResources.getString(R.string.goalComplete), name);
		    notification = new Notification(android.R.drawable.ic_menu_recent_history,
						    contentTitle,
						    System.currentTimeMillis());
		}

		else {
		    
		    contentTitle = String.format(mResources.getString(R.string.cycleComplete), name);
		    notification = new Notification(android.R.drawable.ic_menu_recent_history,
						    contentTitle, System.currentTimeMillis());
		}
		
		contentText = mResources.getString(R.string.noteSelectNext);

		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.defaults |= Notification.DEFAULT_VIBRATE;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;

		Intent notificationIntent = new Intent(Aragorn.this, Aragorn.class);
		PendingIntent contentIntent = PendingIntent.getActivity(Aragorn.this, 0, notificationIntent, PendingIntent.FLAG_ONE_SHOT);
		notification.setLatestEventInfo(getApplicationContext(), contentTitle, contentText, contentIntent);
		
		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(0, notification);
		
		// Now, if we are repeating, reset our time, and register another alarm!
		long id = intent.getLongExtra("id", -1);
		Log.v(TAG, "Seeking id " + id);
		final Endeavor e = findEndeavorById(id);
		
		// If its repeating and it is still the running one
		if(e.isRepeated() && e.equals(mAdapter.getRunningEndeavor()))
		    registerPendingAlarm(e);

		return;
	    }
	}
    }
    
    private class EndeavorListAdapter extends BaseAdapter implements View.OnClickListener {

	private ArrayList<Endeavor> mEndeavors, mRecentEndeavors;
	
	// For the timer
	private volatile boolean mRunning;
	private long mAndroidStartTime, mAndroidPrevTime;
	private long mPrevTime;
	private long mIncrement;
	private long mK;

	// For others
	private Handler mHandler;
	private Endeavor mOn, mPrevOn;
	private TrackingClickHelper mTrackingClicks;
	private PendingIntent mPending;
	boolean mFireImmediately;
	private Context mContext;
	private Resources mResources;

	private Runnable mUpdateTimeTask = new Runnable() {
		public void run() {
		
		    mRunning = true;
		
		    // Doing delta wrt this value is fucked in Stop and Pause modes because
		    // it does not count time spent in deep sleep
		    long now_android = android.os.SystemClock.uptimeMillis();
		    long now = java.lang.System.currentTimeMillis();
		    
		    // Is the timer just starting up?
		    if(mAndroidStartTime == 0) {
			mAndroidStartTime = now_android;
						
			if(mAndroidPrevTime == 0)
			    mAndroidPrevTime = now_android;
			
			mK = 0;
		    }
	
		    // If this is true, then no endeavor has ever been run
		    if(mPrevTime == 0)
			mPrevTime = now;
		    
		    // Calculate the difference in actual times
		    long delta = now - mPrevTime;

		    // Determine if we need to reset the relative android base time
		    if(now_android - mAndroidPrevTime != delta) {

			Log.v(TAG, "Deep sleep detected, resetting relative time base");
			mAndroidStartTime = now_android;
			mK = 0;
		    }

		    // Cache the old values
		    mPrevTime = now;
		    mAndroidPrevTime = now_android;

		    // Increment by the actual difference
		    mOn.increment(delta);
		    
		    // Use the same base time so we don't accumulate error
		    if(!isFinishing()) {
			mHandler.removeMessages(0);
			mHandler.postAtTime(this, mAndroidStartTime + (++mK)*mIncrement);
		    }
		    
		    // Update cumulative stats, use the truncated ones so we consider overall progress
		    updateGlobalStats();
		    notifyDataSetChanged();
		    mRunning = false;
		}
	    };
	
	public EndeavorListAdapter(Context context, ArrayList<Endeavor> list) {

	    super();
	    mOn = null;
	    mRunning = false;
	    mEndeavors = list;
	    mRecentEndeavors = new ArrayList<Endeavor>();
	    mHandler = new Handler();
	    mTrackingClicks = new TrackingClickHelper();
	    mContext = context;
	    mResources = context.getResources();
	}
	
	@Override public int getCount() {

	    return mEndeavors.size() + offsetAdjustment();
	}

	@Override public Endeavor getItem(int position) {

	    // Get item needs to perform translations
	    int numRecent = mRecentEndeavors.size();
	    
	    if(numRecent > 0 && Integer.parseInt(mPrefs.getString("recent_preference", "0")) > 0) {
		
		if(position == 0) {
		    
		    // Should never happen as the separators are not clickable
		    return null;
		}
		else if(position < numRecent + 1) {
		    
		    // return the endeavor for this position with the real list
		    return mRecentEndeavors.get(position - 1); 
		}
		else if(position == numRecent + 1) {

		    // return separator for start of all endeavors
		    return null;
		}
		else if(position > numRecent + 1) {

		    // Take off both separators and the number of recent entries displayed
		    return mEndeavors.get(position - 2 - numRecent);
		}
	    }
	    
	    // Otherwise, its a direct index into the list
	    return mEndeavors.get(position);
	}

	// This really is just a position, not the actual identifier...
	// though if I built this off of a cursor, it probably would become the sex...
	@Override public long getItemId(int position) {
	    
	    Endeavor e = getItem(position);
	    if(e == null)
		return -1;
	    else
		return e.getId();
	}

	@Override public boolean hasStableIds() {
	    
	    return true;
	}

	@Override public boolean isEnabled(int position) {

	    return getItemViewType(position) != SEPARATOR;
	}

	private class TrackingClickHelper implements View.OnClickListener {

	    public void onClick(View v) {
		
		Endeavor ee = (Endeavor)v.getTag();
		ee.setTracked(!ee.isTracked());
		notifyDataSetChanged();
	    }
	}

	// There are goaled and non-goaled endeavors
	@Override public View getView(int position, View convertView, ViewGroup parent) {

	    // google says don't cache DUMBASS :(
	    // Wish that had been written up instead of sitting inside a HOWTO
	    // talk....
	    boolean freshView = false;
	    final Endeavor e = getItem(position);
	    
	    /// BEGIN recent view CODE ///

	    // Did we request a separator?
	    if(e == null) {

		// If we don't have one, make one
		if(convertView == null)
		    convertView = mInflater.inflate(R.layout.list_header, parent, false);
		
		if(position == 0)
		    ((TextView)convertView).setText(mResources.getString(R.string.headingRecents));
		else 
		    ((TextView)convertView).setText(mResources.getString(R.string.headingAll));
		
		// Otherwise, shit hasn't changed
		return convertView;
	    }
	    
	    // END recent view CODE ///
	    
	    if(convertView == null) {
		
		if(e.getGoal() > 0) {
		    convertView = mInflater.inflate(R.layout.goaled_endeavor, parent, false);
		    convertView.setTag(new Integer(GOALED_ENDEAVOR));
		}
		else {
		    convertView = mInflater.inflate(R.layout.endeavor, parent, false);
		    convertView.setTag(new Integer(UNGOALED_ENDEAVOR));
		}
	    
		freshView = true;
	    }	
	    
	    // See if we changed...
	    int oldgoal = ((Integer)convertView.getTag()).intValue();
	    if(oldgoal == GOALED_ENDEAVOR && e.getGoal() == 0) {
		
		convertView = mInflater.inflate(R.layout.endeavor, parent, false);
		convertView.setTag(new Integer(UNGOALED_ENDEAVOR));
		freshView = true;
	    
	    } else if(oldgoal == UNGOALED_ENDEAVOR && e.getGoal() > 0) {
		
		convertView = mInflater.inflate(R.layout.goaled_endeavor, parent, false);
		convertView.setTag(new Integer(GOALED_ENDEAVOR));
		freshView = true;
	    }

	    if(freshView) {
	
		// XXX makes things unresponsive in 100ms mode because??
		// Set the click listeners
	   	convertView.findViewById(R.id.state).setOnClickListener(mAdapter);
		convertView.findViewById(R.id.tracking).setOnClickListener(mTrackingClicks);
	    }
	    
	    // Make the tracking text work, gotta do this every time, because
	    // who knows what endeavor we are getting associated with
	    convertView.findViewById(R.id.tracking).setTag(e);
	    	
	    // Synchronize the view with its underlying data
	    ((TextView)convertView.findViewById(R.id.name)).setText(e.getName());
	    //((TextView)convertView.findViewById(R.id.name)).setText(""+e.getId() + ": " + e.getName());
	    
	    if(e.getGoal() > 0) {
		
		((TextView)convertView.findViewById(R.id.duration)).setText(formatTime(e.getDuration(), mIncrement == 100) + " / " + formatTime(e.getGoal(), mIncrement == 100));
		int progress;
		
		// If its periodic, display the amount accomplished on the current period
		// calculate it from the most recent stamp
		if(e.isRepeated()) {
		    Date d = e.lastStamp();
		    long val;
		    long now = java.lang.System.currentTimeMillis();
		    
		    if(e.equals(mOn))
			val = now - (d == null ? now : d.getTime());
		    else
			val = 0;
		    
		    progress = (int)(((double)(val % e.getGoal())/(double)e.getGoal())*100);
		    Log.v(TAG, "periodic progress: " + progress);
		}
		else
		    progress = (int)((double)e.getDuration()/(double)e.getGoal()*100);
		
		((ProgressBar)convertView.findViewById(R.id.progress)).setProgress(progress);
		
	    }
	    else
		((TextView)convertView.findViewById(R.id.duration)).setText(formatTime(e.getDuration(), mIncrement == 100));
	    
	    View details = convertView.findViewById(R.id.details);

	    if(e.expanded())
		details.setVisibility(View.VISIBLE);
	    else
		details.setVisibility(View.GONE);
	    
	    // Only make this if we need to
	    if(details.getVisibility() == View.VISIBLE) {

		if(e.getStamps().isEmpty())
		    ((TextView)convertView.findViewById(R.id.start)).setText(mResources.getString(R.string.detailUnstarted));
		else 		
		    ((TextView)convertView.findViewById(R.id.start)).setText(renderStamps(e));

		if(e.isTracked())
		    ((TextView)convertView.findViewById(R.id.tracking)).setText(R.string.tracked);
		else
		    ((TextView)convertView.findViewById(R.id.tracking)).setText(R.string.untracked);
	    }
	    
	    if(e.equals(mAdapter.getRunningEndeavor()))
		((Checkable)convertView.findViewById(R.id.state)).setChecked(true);
	    else
		((Checkable)convertView.findViewById(R.id.state)).setChecked(false);
	    
	    // So the damn radio button knows who we are
	    convertView.findViewById(R.id.state).setTag(e);
	    
	    return convertView;
	}

	@Override public int getViewTypeCount() {
	    
	    return 3;
	}
	
	@Override public boolean areAllItemsEnabled() {

	    return false;
	}

	@Override public int getItemViewType(int position) {

	    Endeavor e = getItem(position);
	    
	    if(e == null)
		return SEPARATOR;
	    else {
		     
		if(e.getGoal() > 0)
		    return GOALED_ENDEAVOR;
		else
		    return UNGOALED_ENDEAVOR;
	    }
	}

	public int offsetAdjustment() {
	    
	    if(mRecentEndeavors.size() > 0 && Integer.parseInt(mPrefs.getString("recent_preference", "0")) > 0)
		return mRecentEndeavors.size() + 2;
	    
	    return 0;
	}

	public void flushRecents(Endeavor e) {

	    // If its there, take it out!
	    mRecentEndeavors.remove(e);
	}

	
	public void disable() {

	    disable(true);
	}
	public void disable(boolean s) {

	    // If something is on, turn it off
	    if(mOn != null) {
		
		// Stop the timer
		suspend();
		
		if(s) {

		    mOn.stamp(new Date(mAdapter.getLastFire()));
		    
		    Intent writeIntent = new Intent(mContext, DbIntentService.class);
		    writeIntent.putExtra("mId", mOn.getId());
		    writeIntent.putExtra("task", "stamp");
		    startService(writeIntent);
		    //(new Stamper()).run();
		}
	
		// If it is goaled and we are in DND mode, turn the transciever back on
		if(mOn.getGoal() > 0 && mPrefs.getBoolean("dnd_preference", false)) {
		    
		    Settings.System.putInt(Aragorn.this.getContentResolver(),
					   Settings.System.AIRPLANE_MODE_ON, 0);
		    
		    Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
		    intent.putExtra("state", false);
		    sendBroadcast(intent);
		}

		// Kill any alarm
		killPendingAlarm(mOn);
		
		// Now null it
		mOn = null;
		mAndroidStartTime = 0;
		mAndroidPrevTime = 0;
		mPrevTime = 0;
	    }
	}

	public long[] getRecents() {
	    
	    long[] r = new long[mRecentEndeavors.size()];
	    int k = 0;
	    Iterator<Endeavor> i = mRecentEndeavors.iterator();
	    while(i.hasNext())
		r[k++] = i.next().getId();
	    return r;
	}

	public void restoreRecents(long[] r) {

	    for(int k = 0; k < r.length; ++k)
		mRecentEndeavors.add(findEndeavorById(r[k]));
	}

	// Runs in O(1)
	
	public Endeavor getRunningEndeavor() {
	    
	    return mOn;
	}

	public Endeavor getPreviousEndeavor() {

	    return mPrevOn;
	}

	public long getLastFire() {

	    return mPrevTime;
	}

	// Runs in O(n)
	public void setPrevTime(long t) {

	    mPrevTime = t;
	}

	public int getRunningPosition() {

	    if(mOn == null)
		return -1;
	    
	    Iterator<Endeavor> i = mEndeavors.iterator();
	    
	    int k = 0;
	    while(i.hasNext()) {

		if(mOn.equals(i.next()))
		    return k;
		++k;
	    }
	    
	    return -1000;
	}

	public void setPreviousEndeavor(Endeavor e) {

	    mPrevOn = e;
	}

	public void setRunningEndeavor(Endeavor e) {
	    
	    // If something is running, don't fuck with it and break state
	    if(mOn != null) {
		Log.w(TAG, "Tried to set running while running");
		return;
	    }
	    mOn = e;
	}

	
	public void zeroDelta(boolean b) {

	    mFireImmediately = b;
	    mAndroidStartTime = 0;
	}

	public void suspend() {
	    
	    if(mOn != null) {
	
		// spinlock the UI on running???
		while(mRunning);
		mHandler.removeCallbacks(mUpdateTimeTask);
		mHandler.removeMessages(0);
	    }
	}

	public void resume() {

	    resume(0);
	}

	public void resume(long delay) {
	    
	    // XXX
	    // This is slow code and should NOT be here!
	    if(!mPrefs.getBoolean("show_tenths_preference", false))
		mIncrement = 1000;
	    else
		mIncrement = 100;
	    
	    if(mOn != null) {
		
		mHandler.removeCallbacks(mUpdateTimeTask);
		mHandler.removeMessages(0);
		if(!isFinishing())
		    mHandler.postDelayed(mUpdateTimeTask, delay);
	    }
	}

	public void registerDNDAndAlarm(final boolean oldWasGoaled) {

	    // Handle airplane mode as required
	    if(mPrefs.getBoolean("dnd_preference", false)) {
		
		(new Runnable() {

			public void run() {
			    
			    // thx to http://dustinbreese.blogspot.com/2009/04/andoid-controlling-airplane-mode.html
			    if(oldWasGoaled && mOn.getGoal() == 0) {
				
				// Turn it off
				Settings.System.putInt(Aragorn.this.getContentResolver(),
						       Settings.System.AIRPLANE_MODE_ON, 0);
				Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
				intent.putExtra("state", false);
				sendBroadcast(intent);
			    }
			    else if(!oldWasGoaled && mOn.getGoal() > 0) {
				
				// Turn it on
				Settings.System.putInt(Aragorn.this.getContentResolver(),
						       Settings.System.AIRPLANE_MODE_ON, 1);
				Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
				intent.putExtra("state", true);
				sendBroadcast(intent);
			    }
			}
		    }).run();
	    }

	    // Set up an alarm for it if we have a goal and it hasn't already passed
	    if(mOn.getGoal() > 0)
		registerPendingAlarm(mOn);
	}

	public void pushRecents(Endeavor e) {

	    // Bump mRecentEndeavors, take off the eldest
	    int recents = Integer.parseInt(mPrefs.getString("recent_preference", "0"));
	    boolean wasIn = false;
	    boolean removed = false;
	    
	    wasIn = mRecentEndeavors.contains(e);
	    
	    // Prune list down to size if size was adjusted
	    if(mRecentEndeavors.size() < (recents > 2 ? recents : 2) && !wasIn)
		mRecentEndeavors.add(0,e);
	    else {

		while(mRecentEndeavors.size() > (recents > 2 ? recents : 2)) {
		    removed = removed || mRecentEndeavors.get(mRecentEndeavors.size()-1).equals(e);
		    mRecentEndeavors.remove(mRecentEndeavors.size()-1);
		}
	    
		// If we took it off, put it back on at the bottom (keep it close to where it was, so preserve order)
		// Otherwise put it at the top
		if(wasIn && removed) {
		    mRecentEndeavors.remove(mRecentEndeavors.size()-1);
		    mRecentEndeavors.add(mRecentEndeavors.size()-1, e);
		}
		else if(!wasIn) {
		    mRecentEndeavors.remove(mRecentEndeavors.size()-1);
		    mRecentEndeavors.add(0, e);
		}
	    }
	}

	public void onClick(View v) {
	    
	    Endeavor e = (Endeavor)v.findViewById(R.id.state).getTag();
	    final boolean oldWasGoaled;
	    boolean graced = false;
	    
	    Date pressStamp = new Date(mAdapter.getLastFire());
	    
	    // Is something on?
	    if(mOn != null) {
		
		// If we clicked on ourselves, see if toggle is on
		if(mOn.equals(e)) {
		    
		    if(!mPrefs.getBoolean("toggle_preference", true) || mRecentEndeavors.size() < 2 ||
		       mPrevOn == mOn || mPrevOn == null)
			return;

		    // We want to toggle, so set e to what was running before
		    e = mPrevOn;
		    (Toast.makeText(mContext, String.format(mResources.getString(R.string.qtSuccess), mPrevOn.getName()), Toast.LENGTH_SHORT)).show();
		}
		
		// Send a signal to stop the timer for this one
		suspend();

		// Turn off any alarms
		killPendingAlarm(mOn);
		
		oldWasGoaled = mOn.getGoal() > 0;
	    
		// Note above silliness because of final

		// Try this grace period code
		long grace = Integer.parseInt(mPrefs.getString("grace_preference", "0")) * 1000;
		long last = mOn.lastStamp() != null ? mOn.lastStamp().getTime() : 0;
		Log.v(TAG, "grace check: grace = " + grace + ", delta = " + (getLastFire() - last));
		if(grace > 0 && 
		   (getLastFire() - last) < grace &&
		   !mOn.isRepeated() &&
		   !e.isRepeated()) {

		    // Notify
		    (Toast.makeText(mContext, String.format(mResources.getString(R.string.graceSuccess), (getLastFire() - mOn.lastStamp().getTime())/1000), Toast.LENGTH_SHORT)).show();
		    mPrevOn = mOn;
	
		    // Performing reassignment does this 
		    performReassignment(e, mOn);
		    graced = true;
		}
		else {
		    
		}
	    }
	    else
		oldWasGoaled = false;
	    
	    if(!graced) {
		// Stamp out the old one
		if(mOn != null) {
		    mOn.stamp();

		    Intent writeIntent = new Intent(mContext, DbIntentService.class);
		    writeIntent.putExtra("task", "stamp");
		    writeIntent.putExtra("mId", mOn.getId());
		    startService(writeIntent);
		}

		// Switch
		mPrevOn = mOn;
		mOn = e;
		
		// Stamp the new one
		mOn.stamp();
		
		Intent writeIntent = new Intent(mContext, DbIntentService.class);
		writeIntent.putExtra("task", "stamp");
		writeIntent.putExtra("mId", mOn.getId());
		startService(writeIntent);
	    }
	    else
		mOn = e;
	    
	    // Do this
	    registerDNDAndAlarm(oldWasGoaled);

	    // Handle recents
	    pushRecents(mOn);

	    notifyDataSetChanged();
	    resume();
	}
    }
};