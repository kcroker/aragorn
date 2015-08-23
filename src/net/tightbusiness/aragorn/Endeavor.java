/*********************************
 * 
 * Aragorn Self-Optimization
 * Endeavor.java
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
 * Endeavor.java
 * Copyright(c) 2011 Kevin Croker
 * 
 * Released under GNU General Public License, GPLv3
 *
 **********************************/

package net.tightbusiness.aragorn;

import java.util.Date;
import java.util.ArrayList;
import java.util.Iterator;
import android.os.Bundle;

public class Endeavor {

    // Static max id
    public static long uniqueId = -1;

    // Internal id
    private long mId;
    
    // Name
    private String name;

    // Times
    private long goal;
    private long duration;
    private ArrayList<Date> mStamps;

    // Track me?
    private boolean track;
    private boolean repeat;

    ///////////////////// END ATTRS  ////////////////////////////

    private boolean expanded;

    //////////////////// END UI ////////////////////////////

    public Endeavor() {
        
	mStamps = new ArrayList<Date>();
    }

    public Endeavor(String n, long g, long d, boolean t) {

	uniqueId++;
	mStamps = new ArrayList<Date>();
	goal = g;
	duration = d;
	track = t;
	name = n;
	mId = uniqueId;
	expanded = false;
	repeat = false;
    }
    
    public Endeavor(Bundle b) {

	// Restore the stamps
	mStamps = new ArrayList<Date>();
	long[] slongs = b.getLongArray("stamps");
	int k = 0;
	int K = slongs.length;
		
	while(k < K)
	    mStamps.add(new Date(slongs[k++]));
		
	goal = b.getLong("goal");
	duration = b.getLong("duration");
	track = b.getBoolean("track");
	name = b.getString("name");
	mId = b.getLong("mId");
	expanded = b.getBoolean("expanded");
	repeat = b.getBoolean("repeat");
    }

    /** accesors */
    public long getDuration() {

	return duration;
    }

    public long getGoal() {

	return goal;
    }

    public boolean isTracked() {

	return track;
    }
    
    public boolean isRepeated() {

	return repeat;
    }

    public String getName() {

	return name;
    }

    public boolean expanded() {

	return expanded;
    }

    public void expanded(boolean e) {

	expanded = e;
    }

    public ArrayList<Date> getStamps() {

	return mStamps;
    }

    public long getId() {

	return mId;
    }

    /** mutators */
    public void stamp() {

	mStamps.add(new Date());
    }

    public void stamp(Date hur) {

	mStamps.add(hur);
    }

    public Date lastStamp() {

	if(mStamps.isEmpty())
	    return null;
	else
	    return mStamps.get(mStamps.size()-1);
    }

    public Bundle emitBundle() {

	Bundle s = new Bundle();
	
	Iterator<Date> i = mStamps.iterator();
	long[] slongs = new long[mStamps.size()];
	int k = 0;
	while(i.hasNext()) 
	    slongs[k++] = i.next().getTime();
	
	// Add the stamps
	s.putLongArray("stamps", slongs);
	
	// Add the other stuff
	s.putLong("goal", goal);
	s.putLong("duration", duration);
	s.putBoolean("track", track);
	s.putString("name", name);
	s.putLong("mId", mId);
	s.putBoolean("expanded", expanded);
	s.putBoolean("repeat", repeat);
	
	return s;
    }

    public long setId(long id) {

	return mId = id;
    }

    public void setName(String n) {

	name = n;
    }

    public void setGoal(long g) {

	goal = g;
    }
    
    public void clearStamps() {

	mStamps.clear();
    }

    public void increment(long d) {

	duration += d;
    }
    
    public void setDuration(long d) {

	duration = d;
    }

    public long liveDuration() {

	Iterator<Date> i = mStamps.iterator();
	long k = mStamps.size();
	long sum = 0;
	
	while(i.hasNext() && k > 1) {
	 
	    sum += -(i.next().getTime() - i.next().getTime());
	    k -= 2;
	}
	
	// And then add the last
	sum += (new Date()).getTime() - i.next().getTime();
	return sum;
    }

    public void setTracked(boolean b) {

	track = b;
    }

    public void setRepeated(boolean b) {

	repeat = b;
    }

    public String toString() {

	return "Name: " + name + ", Duration: " + duration + ", Goal: " + goal + ", Tracked: " + track;
    }

    public boolean equals(Endeavor b) {

	if(b != null)
	    return mId == b.mId;
	else
	    return false;
    }

};
