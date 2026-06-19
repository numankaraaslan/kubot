package dev.kubot.model;

public record PodRow(String name, String phase, String ready, int restarts, String age, String owner)
{
}
