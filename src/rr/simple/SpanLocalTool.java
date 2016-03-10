/******************************************************************************

Copyright (c) 2010, Cormac Flanagan (University of California, Santa Cruz)
                    and Stephen Freund (Williams College)

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

 * Neither the names of the University of California, Santa Cruz
      and Williams College nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 ******************************************************************************/

package rr.simple;

import acme.util.Util;
import acme.util.decorations.Decoration;
import acme.util.decorations.DecorationFactory;
import acme.util.decorations.DefaultValue;
import rr.annotations.Abbrev;
import rr.event.AccessEvent;
import rr.event.NewThreadEvent;
import rr.event.ReleaseEvent;
import rr.event.VolatileAccessEvent;
import rr.instrument.java.lang.System;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.tool.Tool;
import acme.util.option.CommandLine;
import tools.util.LockSet;

import java.util.HashSet;

/**
 * Identifies span-local redundancies and filters them out.
 */

@Abbrev("SL")
final public class SpanLocalTool extends Tool {

    public static class SpanLocalSet implements ShadowVar {

        private HashSet<Object> spanAccessedTargets = new HashSet<>();

        private SpanLocalSet() {
        }

        public HashSet get() {
            return spanAccessedTargets;
        }

        public void add(Object o) {
            spanAccessedTargets.add(o);
        }
        
        public boolean contains(Object o) {
        	return spanAccessedTargets.contains(o);
        }

        public boolean isEmpty() {
            return spanAccessedTargets.isEmpty();
        }

        public void empty() {
            spanAccessedTargets.clear();
        }
    }

    static Decoration<ShadowThread,SpanLocalSet> threadSpan =
            ShadowThread.makeDecoration("foo",
                    DecorationFactory.Type.SINGLE,
                    new DefaultValue<ShadowThread, SpanLocalSet>() {
                        public SpanLocalSet get(ShadowThread st) { return new SpanLocalSet(); }});

    public SpanLocalTool(String name, Tool next, CommandLine commandLine) {
        super(name, next, commandLine);
    }

    @Override
    public void access(AccessEvent fae) {
        if(shouldAccess(fae.getTarget(), fae.getThread())) {
            super.access(fae);
        }
    }

    public void logf(ShadowThread currentThread, String s) {
        Util.printf(currentThread + " : " +s);
    }

    public boolean shouldAccess(Object o, ShadowThread t) {
        SpanLocalSet g = threadSpan.get(t);
        if(!g.contains(o)) {
            g.add(o);
//            logf(t, "NOT SKIPPING");
            return true;
        } else {
//            logf(t, "SKIPPING");
            return false;

        }
    }

    @Override
    public void volatileAccess(VolatileAccessEvent fae) {
        if(shouldAccess(fae.getTarget(), fae.getThread())) {
            super.access(fae);
        }
    }

    @Override
    public void create(rr.event.NewThreadEvent e) {
        threadSpan.get(e.getThread()).empty();
        super.create(e);
    }

    @Override
    public void release(ReleaseEvent re)
    {
        threadSpan.get(re.getThread()).empty();
    }

    // public static boolean readFastPath(ShadowVar vs, ShadowThread ts) {
    //     return vs == ts;
    // }

    // public static boolean writeFastPath(ShadowVar vs, ShadowThread ts) {
    //     return vs == ts;
    // }

}
