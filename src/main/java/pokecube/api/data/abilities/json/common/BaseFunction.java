package pokecube.api.data.abilities.json.common;

import java.util.function.Function;

public abstract class BaseFunction<T, R> implements Function<T, R>
{
    public void init()
    {}
}
