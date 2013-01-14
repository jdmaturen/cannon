package com.idlerice.cannon;

import com.google.common.base.Objects;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;
import com.yammer.metrics.reporting.ConsoleReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Cannon, a distributed systems modelling tool.
 *
 * @author JD Maturen
 */
public class Cannon {

    private final static Logger logger = LoggerFactory.getLogger(Cannon.class);

    private final Timer responses = Metrics.newTimer(Cannon.class, "responses", TimeUnit.MILLISECONDS, TimeUnit.SECONDS);


    /**
     * Basic Server representation
     */
    private abstract class Server {
        public abstract long response();

        /**
         * request
         */
        public void request() {
            final TimerContext context = responses.time();
            try {
                Thread.sleep(response());
            } catch (InterruptedException e) {
                // pass
            } finally {
                context.stop();
            }
        }
    }

    /**
     * A server that has an exponential variate response time
     */
    private class ExpovariateServer extends Server {
        private final Random random = new Random();
        private final int average;

        public ExpovariateServer(int average) {
            this.average = average;
        }

        public long response() {
            return Math.round(-Math.log(random.nextDouble())/(1f/average));
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("avg", average).toString();
        }
    }

    /**
     * A server with a uniform response time
     */
    private class UniformServer extends Server {
        private final Random random = new Random();
        private final int average;

        public UniformServer(int average) {
            this.average = average;
        }

        public long response() {
            return Math.round(random.nextDouble() * average);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("avg", average).toString();
        }
    }

    /**
     * A server that periodically freaks out and takes 100x longer than normal to respond for some duration of time
     */
    private class FreakOutServer extends Server {
        private final Random random = new Random();
        private final long startTime = System.currentTimeMillis();

        private boolean freakingOut = false;

        private final int average;
        private final int period;
        private final int duration;


        public FreakOutServer(int average, int period, int duration) {
            this.average = average;
            this.period = period;
            this.duration = duration;
        }

        public long response() {
            final long mod = (System.currentTimeMillis()/1000 - startTime/1000) % period;
            if (mod > 0 && mod < duration) {
                if (!freakingOut)
                    logger.info("gonna freak out now");
                freakingOut = true;
            } else {
                if (freakingOut)
                    logger.info("not freaking out now");
                freakingOut = false;
            }

            if (!freakingOut)
                return 1L * average;
            return 100L * average;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("avg", average)
                    .add("per", period)
                    .add("dur", duration)
                    .toString();
        }
    }

    interface Strategy {
        public void request();
    }

    /**
     * Naively pick a random server to send the request to.
     */
    class RandomStrategy implements Strategy {
        final private Random random = new Random();
        final private List<Server> servers;

        public RandomStrategy(List<Server> servers) {
            this.servers = servers;
        }

        /**
         * Sends request to a random server
         */
        public void request() {
            int index = (int) Math.floor(random.nextDouble() * servers.size());
            servers.get(index).request();
        }
    }

    /**
     * Consistently send our request to thread id hash modulo servers list
     */
    class AffinityStrategy implements Strategy {
        final private List<Server> servers;
        private HashFunction hf = Hashing.goodFastHash(128);

        public AffinityStrategy(List<Server> servers) {
            this.servers = servers;
        }

        /**
         * Based on the current Thread id pick a consistent server from the list to send the request to.
         */
        public void request() {
            HashCode hc = hf.newHasher().putLong(Thread.currentThread().getId()).hash();
            int index = Math.abs(hc.asInt()) % servers.size();
            servers.get(index).request();
        }
    }

    private void main(String strategyName) {
        List<Server> servers = Arrays.asList(
                new ExpovariateServer(10),
                new ExpovariateServer(11),
                new FreakOutServer(10, 60, 20));

        logger.info("servers: {}", servers.toString());

        Strategy tmp;
        if (strategyName.toLowerCase().equals("random")) {
            logger.info("Using random strategy");
            tmp = new RandomStrategy(servers);
        } else {
            logger.info("Using affinity strategy");
            tmp = new AffinityStrategy(servers);
        }

        final Strategy strategy = tmp;

        List<Thread> threads = new ArrayList<Thread>();

        for (int i=0; i<8; i++) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        strategy.request();
                    }
                }
            };
            Thread t = new Thread(r);
            t.setDaemon(true);
            threads.add(t);
        }

        logger.info("Starting {} client threads", threads.size());
        for (Thread t : threads)
            t.start();

        long count = 0L;
        while (true) {
            long t = System.currentTimeMillis();
            long tmpCount = responses.count();
            logger.info("{} req/sec", tmpCount - count);
            count = tmpCount;
            try {
                Thread.sleep(1000L - (System.currentTimeMillis() - t));
            } catch (InterruptedException e) {
                logger.error("interrupted");
                break;
            }
        }
        logger.info("goodbye");
    }


    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("usage: ./Cannon (random|affinity)");
            System.exit(-1);
        }

        logger.info("Hello");
        // I am truly lazy
        Cannon foo = new Cannon();
        foo.main(args[0]);
    }
}
