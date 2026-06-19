package dev.kubot.service;

import io.kubernetes.client.util.Yaml;

public final class YamlSupport
{
    private YamlSupport()
    {
    }

    public static String dump(Object value)
    {
        if (value == null)
        {
            return "";
        }
        try
        {
            return Yaml.dump(value);
        }
        catch (RuntimeException ex)
        {
            return value.toString();
        }
    }
}
