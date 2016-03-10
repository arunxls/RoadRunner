package rr.simple;

import acme.util.Util;
import rr.annotations.Abbrev;
import rr.event.*;
import rr.instrument.java.lang.System;
import rr.meta.SourceLocation;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.tool.Tool;
import acme.util.option.CommandLine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Abbrev("LP")
public class LocalPermutationalTool extends Tool {

    public static class Trie implements ShadowVar {

        protected HashMap<Object, Trie> root;
        protected HashSet<Object> stack;

        public Trie(ShadowThread t) {
            this.root = new HashMap<>();
            this.stack = new HashSet<>();
            this.stack.add(t);
        }

        public void put(Object prev_prefix, Iterator<Object> prefix_it, ShadowThread t) {
            Trie new_trie = new Trie(t);
//            synchronized (this) {
                this.root.put(prev_prefix, new_trie);
//            }
            new_trie.put(prefix_it, t);
        }

        public void put(Iterator<Object> prefix_it, ShadowThread t) {
            Trie current_root = this;
            while(prefix_it.hasNext()) {
                Trie new_trie = new Trie(t);
//                synchronized (this) {
                    current_root.root.put(prefix_it.next(), new_trie);
//                }
                current_root = new_trie;
            }
        }

        public void put(LinkedHashSet<Object> prefix, ShadowThread t) {
            this.put(prefix.iterator(), t);
        }

        public boolean shouldFilter(LinkedHashSet<Object> prefix, AccessEvent fae) {
            Iterator<Object> prefix_it = prefix.iterator();
            ShadowThread t = fae.getThread();

            Object current_prefix = null;
            Trie current_root = this;
            //This step finds if we can find the given prefix.
            while(prefix_it.hasNext()) {
                current_prefix = prefix_it.next();
                if(current_root.root.containsKey(current_prefix)) {
                    current_root = current_root.root.get(current_prefix);
                } else {
                    break;
                }

                current_prefix = null;
            }

            if(!prefix_it.hasNext() && current_prefix == null) {
                //This means we have reached the end of our search
                if(current_root.stack.contains(fae.getThread())) {
                    return true;
                } else if((fae.isWrite() && current_root.stack.size() == 2)) {
                    return true;
                } else if(!fae.isWrite() && current_root.stack.size() == 1) {
                    return true;
                } else {
                    current_root.stack.add(t);
                    return false;
                }
            } else {
                //This means the current context was not found
                current_root.put(current_prefix, prefix_it, t);
                return false;
            }
        }
    }

    static final Integer MAX_THREADS = 200;

    static private ArrayList<LinkedHashSet<Object>> threadContext = new ArrayList<>(MAX_THREADS);
//    static private ConcurrentHashMap<SourceLocation, Trie> LocContext = new ConcurrentHashMap<>(21966, 0.25f, 1);
//    static private HashMap<SourceLocation, Trie> LocContext = new HashMap<>();
    static private HashMap<Integer, Trie> LocContext = new HashMap<>();
    static private long[] countArray = new long[MAX_THREADS];

    //Constructor
    public LocalPermutationalTool(String name, Tool next, CommandLine commandLine) {
        super(name, next, commandLine);
        for(int i = 0; i < MAX_THREADS; ++i) {
            threadContext.add(new LinkedHashSet<Object>());
        }
    }

    static int maxLoc = 0;

    @Override
    public void access(AccessEvent fae) {
        ShadowThread t = fae.getThread();
        SourceLocation loc = fae.getAccessInfo().getLoc();

        if(maxLoc < loc.getLine()) maxLoc = loc.getLine();

        if(LocContext.containsKey(loc.getLine())) {
            if(!LocContext.get(loc.getLine()).shouldFilter(threadContext.get(fae.getThread().getTid()), fae)) {
                super.access(fae);
            } else {
//                countArray[t.getTid()]++;
            }
        } else {
            Trie new_trie = new Trie(t);
            new_trie.put(threadContext.get(t.getTid()), t);
            LocContext.put(loc.getLine(), new_trie);
            super.access(fae);
        }
    }

    @Override
    public void acquire(AcquireEvent ae) {
        ShadowThread t = ae.getThread();

        threadContext.get(t.getTid()).add(ae.getLock());

        super.acquire(ae);
    }

    @Override
    public void postStart(StartEvent se) {
        ShadowThread t = se.getThread();

        threadContext.get(t.getTid()).add(new Object());

        super.postStart(se);
    }

    @Override
    public void release(ReleaseEvent re) {
        ShadowThread t = re.getThread();

        if(threadContext.get(t.getTid()).contains(re.getLock())) {
//            Util.printf("%s\n","LOCK ACQ NOT FOUND");
            threadContext.get(t.getTid()).remove(re.getLock());
        }
        super.release(re);
    }

    @Override
    public void fini() {
        Long count = 0l;
        for(long i : countArray) {
            count += i;
        }

        Util.printf("%s %d\n","TOTAL SKIPPED: ", count);
//        Util.printf("%s %d\n","MAX LINE: ", maxLoc);
    }

    @Override
    public void volatileAccess(VolatileAccessEvent fae) {
        ShadowThread t = fae.getThread();
        SourceLocation loc = fae.getAccessInfo().getLoc();

        if(maxLoc < loc.getLine()) maxLoc = loc.getLine();

        if(LocContext.containsKey(loc.getLine())) {
            if(!LocContext.get(loc.getLine()).shouldFilter(threadContext.get(fae.getThread().getTid()), fae)) {
                super.volatileAccess(fae);
            } else {
//                countArray[t.getTid()]++;
            }
        } else {
            Trie new_trie = new Trie(t);
            new_trie.put(threadContext.get(t.getTid()), t);
            LocContext.put(loc.getLine(), new_trie);
            super.volatileAccess(fae);
        }
    }

    @Override
    public void preJoin(JoinEvent je) {
        threadContext.set(je.getJoiningThread().getTid(), new LinkedHashSet<>());
        super.preJoin(je);

    }

    @Override
    public void postJoin(JoinEvent je) {
        ShadowThread t = je.getThread();
        threadContext.get(t.getTid()).add(new Object());
        super.postJoin(je);
    }

    @Override
    public void preNotify(NotifyEvent ne) {
        threadContext.get(ne.getLock().getHoldingThread().getTid()).add(new Object());
        super.preNotify(ne);
    }

    @Override
    public void postNotify(NotifyEvent ne) {
        ShadowThread t = ne.getThread();
        threadContext.get(t.getTid()).add(new Object());
        super.postNotify(ne);
    }
}
