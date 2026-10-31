package dev.tr7zw.entityculling;

import java.util.HashSet;
import java.util.Set;

/**
 * Test stand-in for Entity Culling's mod base (tr7zw, client-only, in the tester pack): only the two members
 * EntityCullingCompat reaches by reflection - the static {@code instance} and its {@code tickCullWhitelists}, the entity
 * types whose client tick Entity Culling never skips. Our own code; no Entity Culling code is copied or bundled.
 */
public class EntityCullingModBase {

    public static EntityCullingModBase instance;

    public Set<Object> tickCullWhitelists = new HashSet<>();
}
