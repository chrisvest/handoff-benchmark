package handoffbenchmark;

import java.lang.reflect.Field;
import java.util.concurrent.Exchanger;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.logic.Control;
import sun.misc.Unsafe;

@State( Scope.Benchmark )
public abstract class HandoffBenchmark
{
    public static final Object TOKEN = new Object();

    interface Handoff
    {
        void put(Object obj);
        Object take();
    }

    private Handoff handoff;

    public abstract Handoff createHandoff();

    @Setup
    public void initialiseHandoff()
    {
        handoff = createHandoff();
    }

    @GenerateMicroBenchmark
    @Group("a")
    @GroupThreads(1)
    public void put()
    {
        handoff.put( TOKEN );
    }

    @GenerateMicroBenchmark
    @Group("a")
    @GroupThreads(1)
    public Object take()
    {
        return handoff.take();
    }

    @GenerateMicroBenchmark
    @Group("a")
    @GroupThreads(1)
    public void terminationControl(Control control) throws InterruptedException
    {
        // This tremendous hack of a method is a sign that JMH still has a few
        // limitations here and there...
        // Oh, and if it messes up and lets the benchmark get stuck, try again.
        if (control.stopMeasurement)
        {
            Thread currentThread = Thread.currentThread();
            int insistence = 0;
            int threadsInPreTearDown;
            int workerThreads;
            do {
                threadsInPreTearDown = 0;
                workerThreads = 0;
                Thread[] threads = new Thread[Thread.activeCount()];
                int count = Thread.enumerate( threads );
                for ( int i = 0; i < count; i++ )
                {
                    Thread thread = threads[i];
                    if (thread == currentThread)
                    {
                        continue;
                    }
                    if (thread.getName().contains( "worker" ))
                    {
                        workerThreads++;
                        // We do this join to decrease the probability of observing a
                        // benchmark method, that then moves into the preTearDown by
                        // the time we interrupt it.
                        thread.join( 1 + (insistence * 2) );
                        StackTraceElement[] stackTrace = thread.getStackTrace();
                        boolean isInTearDown = false;
                        for (StackTraceElement element : stackTrace)
                        {
                            if (element.getMethodName().equals( "preTearDown" ))
                            {
                                threadsInPreTearDown++;
                                isInTearDown = true;
                                break;
                            }
                        }

                        if (!isInTearDown)
                        {
                            thread.interrupt();
                        }
                    }
                }
            } while (threadsInPreTearDown < workerThreads || insistence++ < 5);
        }
    }

    public static class JakesBrokenHandoffBenchmark extends HandoffBenchmark
    {
        private static class JakesHandoff implements Handoff
        {
            private static Unsafe unsafe;
            private static long ownerFieldOffset;

            static {
                Field theUnsafe = null;
                try
                {
                    theUnsafe = Unsafe.class.getDeclaredField( "theUnsafe" );
                    theUnsafe.setAccessible( true );
                    unsafe = (Unsafe) theUnsafe.get( null );
                    ownerFieldOffset = unsafe.objectFieldOffset( JakesHandoff.class.getDeclaredField( "owner" ) );
                }
                catch ( NoSuchFieldException e )
                {
                    e.printStackTrace();
                }
                catch ( IllegalAccessException e )
                {
                    e.printStackTrace();
                }
            }

            private final int CLIENT = 1;
            private final int SERVER = 2;

            Object message;

            int owner = CLIENT;

            public boolean waitUntilClientOwnsIt()
            {
                boolean notInterrupted = true;
                while(unsafe.getIntVolatile( this, ownerFieldOffset ) != CLIENT
                        && (notInterrupted = !Thread.interrupted()));
                return notInterrupted;
            }

            public void handOverToServer()
            {
                owner = SERVER;
            }

            public boolean waitUntilServerOwnsIt()
            {
                boolean notInterrupted = true;
                while(unsafe.getIntVolatile( this, ownerFieldOffset ) != SERVER
                        && (notInterrupted = !Thread.interrupted()));
                return notInterrupted;
            }

            public void handOverToClient()
            {
                owner = SERVER;
            }

            @Override
            public void put( Object obj )
            {
                if ( waitUntilClientOwnsIt() )
                {
                    message = obj;
                    handOverToServer();
                }
                else
                {
                    Thread.interrupted(); // clear
                }
            }

            @Override
            public Object take()
            {
                if ( waitUntilServerOwnsIt() )
                {
                    Object obj = message;
                    handOverToClient();
                    return obj;
                }
                Thread.interrupted(); // clear
                return null;
            }
        }

        @Override
        public Handoff createHandoff()
        {
            return new JakesHandoff();
        }
    }
    public static class JakesFixedHandoffBenchmark extends HandoffBenchmark
    {
        private static class JakesHandoff implements Handoff
        {
            private static final int CLIENT = 1;
            private static final int SERVER = 2;

            Object message;

            volatile int owner = CLIENT;

            public boolean waitForOwnership(int ownership)
            {
                boolean notInterrupted = true;
                while(owner != ownership
                        && (notInterrupted = !Thread.interrupted()));
                return notInterrupted;
            }

            public void handOverToServer()
            {
                owner = SERVER;
            }

            public void handOverToClient()
            {
                owner = CLIENT;
            }

            @Override
            public void put( Object obj )
            {
                if ( waitForOwnership(CLIENT) )
                {
                    message = obj;
                    handOverToServer();
                }
                else
                {
                    Thread.interrupted(); // clear
                }
            }

            @Override
            public Object take()
            {
                if ( waitForOwnership(SERVER) )
                {
                    Object obj = message;
                    handOverToClient();
                    return obj;
                }
                Thread.interrupted(); // clear
                return null;
            }
        }

        @Override
        public Handoff createHandoff()
        {
            return new JakesHandoff();
        }
    }

    public static class AtomicHandoffBenchmark extends HandoffBenchmark
    {
        private static class AtomicHandoff extends AtomicReference<Object> implements Handoff
        {

            @Override
            public void put( Object obj )
            {
                while (!compareAndSet( null, obj ) && !Thread.interrupted());
            }

            @Override
            public Object take()
            {
                Object obj;
                while ((obj = getAndSet( null )) == null && !Thread.interrupted());
                return obj;
            }
        }

        @Override
        public Handoff createHandoff()
        {
            return new AtomicHandoff();
        }
    }

    public static class SynchronousQueueHandoffBenchmark extends HandoffBenchmark
    {
        private static class SynchronousQueueHandoff implements Handoff
        {
            private final SynchronousQueue<Object> queue = new SynchronousQueue<Object>();

            @Override
            public void put( Object obj )
            {
                try
                {
                    queue.put( obj );
                }
                catch ( InterruptedException e )
                {
                }
            }

            @Override
            public Object take()
            {
                try
                {
                    return queue.take();
                }
                catch ( InterruptedException e )
                {
                    return null;
                }
            }
        }

        @Override
        public Handoff createHandoff()
        {
            return new SynchronousQueueHandoff();
        }
    }

    public static class ExchangerHandoffBenchmark extends HandoffBenchmark
    {
        private static class ExchangerHandoff extends Exchanger<Object> implements Handoff
        {

            @Override
            public void put( Object obj )
            {
                try
                {
                    exchange( obj );
                }
                catch ( InterruptedException e )
                {
                }
            }

            @Override
            public Object take()
            {
                try
                {
                    return exchange( null );
                }
                catch ( InterruptedException e )
                {
                    return null;
                }
            }
        }

        @Override
        public Handoff createHandoff()
        {
            return new ExchangerHandoff();
        }
    }
}
