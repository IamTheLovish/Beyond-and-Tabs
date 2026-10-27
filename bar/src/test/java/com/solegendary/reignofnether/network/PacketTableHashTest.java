package com.solegendary.reignofnether.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The channel version must follow the packet table and nothing else (security audit F3, Oct 26): the same table gives
 * the same version on every machine and every run (so identical code from different commits connects), and any change
 * a client and server could disagree on - a packet added, removed, moved or turned round - gives another.
 */
class PacketTableHashTest {

    static final List<String> TABLE = List.of(
        PacketTableHash.entry(0, "com.example.PingPacket", "PLAY_TO_SERVER"),
        PacketTableHash.entry(1, "com.example.PongPacket", "PLAY_TO_CLIENT"));

    @Test
    void aKnownTableHasAFixedHashOnEveryMachine() {
        // SHA-256 of "0:com.example.PingPacket:PLAY_TO_SERVER\n1:com.example.PongPacket:PLAY_TO_CLIENT\n", first 12 hex
        // digits, computed outside Java: no hashCode, locale or run-to-run state can leak in
        assertEquals("c73dcd1e4e85", PacketTableHash.hash(TABLE));
        assertEquals("e3b0c44298fc", PacketTableHash.hash(List.of()));
    }

    @Test
    void theSameTableHashesTheSameEveryTime() {
        assertEquals(PacketTableHash.hash(TABLE), PacketTableHash.hash(new ArrayList<>(TABLE)));
        assertEquals(PacketTableHash.protocolVersion("1.5.0-beta-4", TABLE), PacketTableHash.protocolVersion("1.5.0-beta-4", TABLE));
    }

    @Test
    void anAddedPacketChangesTheHash() {
        List<String> more = new ArrayList<>(TABLE);
        more.add(PacketTableHash.entry(2, "com.example.PausePacket", "PLAY_TO_SERVER"));
        assertNotEquals(PacketTableHash.hash(TABLE), PacketTableHash.hash(more));
    }

    @Test
    void aRemovedPacketChangesTheHash() {
        assertNotEquals(PacketTableHash.hash(TABLE), PacketTableHash.hash(TABLE.subList(0, 1)));
    }

    @Test
    void twoPacketsSwappingIndicesChangesTheHash() {
        List<String> swapped = List.of(
            PacketTableHash.entry(0, "com.example.PongPacket", "PLAY_TO_CLIENT"),
            PacketTableHash.entry(1, "com.example.PingPacket", "PLAY_TO_SERVER"));
        assertNotEquals(PacketTableHash.hash(TABLE), PacketTableHash.hash(swapped));
    }

    @Test
    void aPacketChangingDirectionChangesTheHash() {
        List<String> turned = List.of(
            PacketTableHash.entry(0, "com.example.PingPacket", "PLAY_TO_CLIENT"),
            PacketTableHash.entry(1, "com.example.PongPacket", "PLAY_TO_CLIENT"));
        assertNotEquals(PacketTableHash.hash(TABLE), PacketTableHash.hash(turned));
    }

    @Test
    void theVersionKeepsTheModVersionInFront() {
        String v = PacketTableHash.protocolVersion("1.5.0-beta-4", TABLE);
        assertEquals("1.5.0-beta-4+packets.c73dcd1e4e85", v);
    }
}
