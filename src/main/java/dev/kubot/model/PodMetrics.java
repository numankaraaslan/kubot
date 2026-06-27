package dev.kubot.model;

public record PodMetrics(long cpuNanoCores, long cpuLimitNanoCores, long memoryBytes, long memoryLimitBytes)
{
    public double cpuPercent()
    {
        if (cpuLimitNanoCores <= 0)
        {
            return -1;
        }
        return (double) cpuNanoCores / cpuLimitNanoCores * 100.0;
    }

    public double memoryPercent()
    {
        if (memoryLimitBytes <= 0)
        {
            return -1;
        }
        return (double) memoryBytes / memoryLimitBytes * 100.0;
    }

    public String cpuFormatted()
    {
        if (cpuLimitNanoCores > 0)
        {
            return Math.round(cpuPercent()) + "% of " + String.format("%.1f", cpuLimitNanoCores / 1_000_000_000.0) + " cores";
        }
        long milliCores = cpuNanoCores / 1_000_000;
        return milliCores + "m (no limit set)";
    }

    public String memoryFormatted()
    {
        String usage = humanBytes(memoryBytes);
        if (memoryLimitBytes > 0)
        {
            return usage + " / " + humanBytes(memoryLimitBytes) + " (" + Math.round(memoryPercent()) + "%)";
        }
        return usage;
    }

    private static String humanBytes(long bytes)
    {
        if (bytes >= 1024L * 1024 * 1024)
        {
            return String.format("%.1f GiB", (double) bytes / (1024 * 1024 * 1024));
        }
        if (bytes >= 1024L * 1024)
        {
            return String.format("%.0f MiB", (double) bytes / (1024 * 1024));
        }
        if (bytes >= 1024L)
        {
            return String.format("%.0f KiB", (double) bytes / 1024);
        }
        return bytes + " B";
    }
}
