/*
    :: src/LamportClock.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    A convenience class representing a Lamport clock, with synchronized
    methods for retrieving and updating/incrementing the current Lamport time.
    Note that this object is often called from within synchronized blocks in
    other classes. This will not cause deadlocks, provided these methods are
    ALWAYS called as such for objects that share a clock, because Java
    synchronized blocks are reentrant.
*/

package rjww.atom;

import java.io.Serializable;

public class LamportClock implements Serializable {
    private int time = 0;

    // Retrieve the current Lamport time without updating it.
    public synchronized int peek() {
        return this.time;
    }

    // Increment the current Lamport time and return it.
    public synchronized int update() {
        this.time++;
        return this.time;
    }

    // Set the current Lamport time to the maximum of itself and a supplied
    // value, increment the result, and return it.
    public synchronized int update(int otherTime) {
        this.time = Math.max(this.time, otherTime) + 1;
        return this.time;
    }
}
