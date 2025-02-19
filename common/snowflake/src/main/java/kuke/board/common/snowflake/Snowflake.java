package kuke.board.common.snowflake;

import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class Snowflake {
    private static final long EPOCH = 1704067200000L;  // UTC = 2024-01-01T00:00:00Z
    private static final long DATA_CENTER_ID_BITS = 5L;
    private static final long MACHINE_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_DATA_CENTER_ID = (1L << DATA_CENTER_ID_BITS) - 1;
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATA_CENTER_ID_BITS;

    private final long dataCenterId;
    private final long machineId;
    private long sequence = 0L;
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);

	public Snowflake() {
		this.dataCenterId = getDataCenterId();
		this.machineId = getMachineId();
	}

	public long nextId() {
	    long timestamp = currentTimestamp();

	    long lastTime = lastTimestamp.get();
	    if (timestamp < lastTime) {
	        timestamp = lastTime;
	    }

	    long sequenceNum;
	    synchronized (this) {
	        if (timestamp == lastTime) {
	            sequence = (sequence + 1) & MAX_SEQUENCE;
	            if (sequence == 0) {
	                timestamp = waitNextMillis(lastTime);
	            }
	        } else {
	            sequence = 0L;
	        }
	        sequenceNum = sequence;
	        lastTimestamp.set(timestamp);
	    }

	    return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
	            | (dataCenterId << DATA_CENTER_ID_SHIFT)
	            | (machineId << MACHINE_ID_SHIFT)
	            | sequenceNum;
	}

    private long currentTimestamp() {
        return System.currentTimeMillis();
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

	private static long getDataCenterId() {
		String envDataCenterId = System.getenv("DATA_CENTER_ID");
		if (envDataCenterId != null) {
			try {
				long id = Long.parseLong(envDataCenterId);
				if (id >= 0 && id <= MAX_DATA_CENTER_ID) {
					return id;
				}
			} catch (NumberFormatException ignored) { }
		}
		return ThreadLocalRandom.current().nextInt((int) MAX_DATA_CENTER_ID + 1); // 랜덤 값
	}

	private static long getMachineId() {
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();
				byte[] mac = networkInterface.getHardwareAddress();
				if (mac != null) {
					int hash = 0;
					for (byte b : mac) {
						hash += (b & 0xFF);
					}
					return hash % (MAX_MACHINE_ID + 1);
				}
			}
		} catch (Exception ignored) { }
		return ThreadLocalRandom.current().nextInt( (int) MAX_MACHINE_ID + 1); // 랜덤 값
	}
}