package com.santiago43rus.rupoop.lan

import com.santiago43rus.rupoop.data.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private var checks = 0
private fun ck(c: Boolean, msg: String) { checks++; if (!c) throw AssertionError("FAIL: $msg") }

private fun norm(r: UserRegistry) = r.copy(appSettings = AppSettings(), lastSynced = 0).toString()
private fun hash(r: UserRegistry) = LanCrypto.hex(LanCrypto.sha256(norm(r).toByteArray()))

private fun sr(i: Int) = SearchResult(videoUrl = "https://rutube.ru/video/v$i/", title = "t$i", hits = if (i % 3 == 0) i else null)

// ---------- RegistryMerge ----------
private class Replica(var r: UserRegistry = UserRegistry(lastSynced = 0)) {
    fun change(clock: AtomicLong, f: (UserRegistry) -> UserRegistry) {
        val n = f(r)
        r = RegistryKeys.stampLocalChange(r, n, clock.incrementAndGet())
    }
}

private fun testMergeBasics() {
    val clock = AtomicLong(1_000_000_000_000L)
    val a = Replica(); val b = Replica()
    a.change(clock) { it.copy(likedVideos = listOf(sr(1), sr(2))) }
    var m = RegistryMerge.merge(a.r, b.r, clock.get()); a.r = m; b.r = m
    ck(b.r.likedVideos.size == 2, "like transferred")
    // A удаляет, B не знает
    a.change(clock) { it.copy(likedVideos = it.likedVideos.filter { v -> v != sr(1) }) }
    m = RegistryMerge.merge(b.r, a.r, clock.get())
    ck(m.likedVideos == listOf(sr(2)), "delete propagates: ${m.likedVideos}")
    a.r = m; b.r = m
    // повторное добавление побеждает надгробие
    b.change(clock) { it.copy(likedVideos = listOf(sr(1)) + it.likedVideos) }
    m = RegistryMerge.merge(a.r, b.r, clock.get())
    ck(m.likedVideos.map { it.title } == listOf("t1", "t2"), "re-add wins, order kept: ${m.likedVideos.map { it.title }}")
    // история: удаление и повторный просмотр
    a.change(clock) { it.copy(watchHistory = listOf(WatchHistoryItem("h1", clock.get(), 5, 100))) }
    b.r = RegistryMerge.merge(b.r, a.r, clock.get())
    b.change(clock) { it.copy(watchHistory = emptyList()) }
    a.r = RegistryMerge.merge(a.r, b.r, clock.get())
    ck(a.r.watchHistory.isEmpty(), "history delete propagates")
    a.change(clock) { it.copy(watchHistory = listOf(WatchHistoryItem("h1", clock.incrementAndGet(), 9, 100))) }
    b.r = RegistryMerge.merge(b.r, a.r, clock.get())
    ck(b.r.watchHistory.size == 1 && b.r.watchHistory[0].progress == 9L, "history re-watch revives")
    // плейлисты
    a.change(clock) { it.copy(playlists = listOf(Playlist("1", "P", listOf(sr(5), sr(6))))) }
    b.r = RegistryMerge.merge(b.r, a.r, clock.get())
    b.change(clock) { it.copy(playlists = it.playlists.map { p -> p.copy(videos = p.videos.filter { v -> v != sr(5) }) }) }
    a.r = RegistryMerge.merge(a.r, b.r, clock.get())
    ck(a.r.playlists.single().videos == listOf(sr(6)), "playlist item removal propagates")
    b.change(clock) { it.copy(playlists = emptyList()) }
    a.r = RegistryMerge.merge(a.r, b.r, clock.get())
    ck(a.r.playlists.isEmpty(), "playlist delete propagates")
    // миграция: порядок старых данных сохраняется
    val legacy = UserRegistry(likedVideos = listOf(sr(9), sr(8), sr(7)), lastSynced = 0)
    val mig = RegistryKeys.migrateAddedAt(legacy)
    val mm = RegistryMerge.merge(mig, UserRegistry(lastSynced = 0), clock.get())
    ck(mm.likedVideos == legacy.likedVideos, "legacy order kept")
    // легаси-пир с тем же элементом не воскрешает свежее удаление
    val legacyPeer = RegistryKeys.migrateAddedAt(UserRegistry(likedVideos = listOf(sr(2)), lastSynced = 0))
    val cur = Replica(RegistryKeys.migrateAddedAt(UserRegistry(likedVideos = listOf(sr(2)), lastSynced = 0)))
    cur.change(clock) { it.copy(likedVideos = emptyList()) }
    ck(RegistryMerge.merge(cur.r, legacyPeer, clock.get()).likedVideos.isEmpty(), "fresh delete beats legacy copy")
}

private fun testMergeRandom() {
    val rnd = Random(42)
    repeat(300) { round ->
        val clock = AtomicLong(1_000_000_000_000L + round)
        val reps = listOf(Replica(), Replica(), Replica())
        repeat(120) {
            val r = reps[rnd.nextInt(3)]
            when (rnd.nextInt(12)) {
                0, 1 -> r.change(clock) { x -> x.copy(likedVideos = (listOf(sr(rnd.nextInt(12))) + x.likedVideos).distinctBy { it.videoUrl }) }
                2 -> r.change(clock) { x -> x.copy(likedVideos = x.likedVideos.filterIndexed { i, _ -> i != 0 }) }
                3 -> r.change(clock) { x -> x.copy(watchLater = (listOf(sr(rnd.nextInt(12))) + x.watchLater).distinctBy { it.videoUrl }) }
                4 -> r.change(clock) { x -> x.copy(watchLater = x.watchLater.drop(1)) }
                5 -> r.change(clock) { x -> x.copy(searchHistory = (listOf("q${rnd.nextInt(30)}") + x.searchHistory).distinct().take(20)) }
                6 -> r.change(clock) { x -> x.copy(watchHistory = (listOf(WatchHistoryItem("h${rnd.nextInt(15)}", clock.incrementAndGet(), rnd.nextLong(100), 100)) + x.watchHistory).distinctBy { it.videoId }.take(500)) }
                7 -> r.change(clock) { x -> x.copy(watchHistory = x.watchHistory.drop(1)) }
                8 -> r.change(clock) { x ->
                    val n = "P${rnd.nextInt(3)}"
                    val ex = x.playlists.firstOrNull { it.name == n }
                    val v = sr(rnd.nextInt(8))
                    if (ex == null) x.copy(playlists = x.playlists + Playlist("id${rnd.nextInt(1000)}", n, listOf(v)))
                    else x.copy(playlists = x.playlists.map { p -> if (p.name == n) p.copy(videos = (listOf(v) + p.videos).distinctBy { it.videoUrl }) else p })
                }
                9 -> r.change(clock) { x -> x.copy(playlists = x.playlists.drop(1)) }
                10 -> r.change(clock) { x -> x.copy(subscriptions = (x.subscriptions + Author(name = "a${rnd.nextInt(6)}")).distinctBy { it.name }) }
                else -> {
                    val o = reps[rnd.nextInt(3)]
                    val m = RegistryMerge.merge(r.r, o.r, clock.incrementAndGet())
                    r.r = m
                    // коммутативность и идемпотентность
                    val m2 = RegistryMerge.merge(o.r, r.r, clock.get())
                    val m3 = RegistryMerge.merge(r.r, o.r, clock.get())
                    ck(hash(RegistryMerge.merge(m3, m3, clock.get())) == hash(m3), "idempotent")
                    ck(hash(m2) == hash(m3), "commutative (random)")
                }
            }
        }
        // полный обмен «звездой»: A<->B, A<->C, A<->B
        fun exchange(x: Replica, y: Replica) {
            val m = RegistryMerge.merge(x.r, y.r, clock.incrementAndGet())
            // коммутативность
            ck(hash(RegistryMerge.merge(y.r, x.r, clock.get())) == hash(m), "commutative round $round")
            x.r = m
            y.r = RegistryMerge.merge(y.r, m, clock.get())   // клиент применяет ответ сервера
            ck(hash(x.r) == hash(y.r), "pair converged round $round")
        }
        exchange(reps[0], reps[1]); exchange(reps[0], reps[2]); exchange(reps[0], reps[1])
        exchange(reps[1], reps[2]); exchange(reps[0], reps[1])
        ck(hash(reps[0].r) == hash(reps[1].r), "A=B round $round")
        ck(hash(reps[1].r) == hash(reps[2].r), "B=C round $round")
    }
}

// ---------- крипто и QR ----------
private fun testCrypto() {
    fun h(s: String) = LanCrypto.unhex(s)
    val okm = LanCrypto.hkdf(h("0b".repeat(22)), h("000102030405060708090a0b0c"), h("f0f1f2f3f4f5f6f7f8f9"), 42)
    ck(LanCrypto.hex(okm) == "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", "hkdf RFC5869 vector")
    val a = LanCrypto.newEphemeral(); val b = LanCrypto.newEphemeral()
    ck(a.publicKey.size == 91, "pub size ${a.publicKey.size}")
    ck(LanCrypto.hex(a.agree(b.publicKey)) == LanCrypto.hex(b.agree(a.publicKey)), "ecdh agree")
    val pk = LanCrypto.randomBytes(32); val nc = LanCrypto.randomBytes(16); val ns = LanCrypto.randomBytes(16)
    val c = LanCrypto.Channel.client(pk, nc, ns); val s = LanCrypto.Channel.server(pk, nc, ns)
    val m1 = c.seal("привет".toByteArray()); val m2 = c.seal("привет".toByteArray())
    ck(!m1.contentEquals(m2), "iv differs")
    ck(String(s.open(m1)) == "привет", "open 1")
    var failed = false
    try { s.open(m1) } catch (_: java.security.GeneralSecurityException) { failed = true }
    ck(failed, "replay rejected")
    val s2 = LanCrypto.Channel.server(pk, nc, ns)
    failed = false
    try { s2.open(m2) } catch (_: java.security.GeneralSecurityException) { failed = true }
    ck(failed, "reorder rejected")
    val wrong = LanCrypto.Channel.server(LanCrypto.randomBytes(32), nc, ns)
    failed = false
    try { wrong.open(m1) } catch (_: java.security.GeneralSecurityException) { failed = true }
    ck(failed, "wrong key rejected")
    // QR
    val d = PairingQr.Data(LanCrypto.hex(LanCrypto.randomBytes(16)), "192.168.1.50", 47931, LanCrypto.randomBytes(32), "BBK ТВ & кино")
    val link = PairingQr.build(d)
    val p = PairingQr.parse(link)!!
    ck(p.host == d.host && p.port == d.port && p.name == "BBK ТВ & кино" && p.key.contentEquals(d.key) && p.deviceId == d.deviceId, "qr roundtrip")
    ck(link.length < 200, "qr length ${link.length}")
    ck(PairingQr.parse(link.replace("192.168.1.50", "8.8.8.8")) == null, "public ip rejected")
    ck(PairingQr.parse("https://example.com") == null, "foreign url rejected")
    ck(PairingQr.parse(link.replace("v=1", "v=2")) == null, "version rejected")
}

// ---------- протокол ----------
private class MemStore : PeerStore {
    private val m = ConcurrentHashMap<String, LanPeer>()
    override fun all() = m.values.toList()
    override fun get(deviceId: String) = m[deviceId]
    override fun put(peer: LanPeer) { m[peer.deviceId] = peer }
    override fun remove(deviceId: String) { m.remove(deviceId) }
}

private val jsonBox = ConcurrentHashMap<String, UserRegistry>()
private class MemAccess(var reg: UserRegistry) : RegistryAccess {
    val clock = AtomicLong(2_000_000_000_000L)
    @Synchronized override fun hash() = hash(reg)
    @Synchronized override fun snapshot(): String { val id = java.util.UUID.randomUUID().toString(); jsonBox[id] = reg; return id }
    @Synchronized override fun mergeAndApply(remoteJson: String): String {
        reg = RegistryMerge.merge(reg, jsonBox[remoteJson]!!, clock.incrementAndGet())
        val id = java.util.UUID.randomUUID().toString(); jsonBox[id] = reg; return id
    }
}

private fun ident(name: String) = LanIdentity(LanCrypto.randomBytes(16), name)

private fun testProtocol() {
    val tvId = ident("BBK TV"); val phId = ident("Pixel")
    val tvStore = MemStore(); val phStore = MemStore()
    val clock = AtomicLong(3_000_000_000_000L)
    val tvAcc = MemAccess(UserRegistry(lastSynced = 0)); val phAcc = MemAccess(UserRegistry(lastSynced = 0))
    var decision = true
    var asked = 0
    val tv = LanServer(tvId, tvStore, tvAcc, { _, _ -> asked++; decision })
    val port = tv.start(0)
    val ph = LanClient(phId, phStore, phAcc)

    fun offer(ms: Long = 120_000) = PairingOffer(LanCrypto.randomBytes(32), System.currentTimeMillis() + ms).also { tv.offer = it }
    fun qr(o: PairingOffer, key: ByteArray = o.key) = PairingQr.Data(tvId.idHex, "127.0.0.1", port, key, "BBK TV")

    // без QR молча закрывает
    var o = offer(); tv.offer = null
    ck(ph.pair(qr(o), 0) is PairOutcome.Expired, "no offer -> expired")

    // неверный ключ
    o = offer()
    ck(ph.pair(qr(o, LanCrypto.randomBytes(32)), 0) is PairOutcome.Expired, "wrong key -> closed")
    ck(o.failures == 1 && asked == 0, "failure counted, user not asked")
    repeat(4) { ph.pair(qr(o, LanCrypto.randomBytes(32)), 0) }
    ck(o.failures == 5, "five failures")
    ck(ph.pair(qr(o), 0) is PairOutcome.Expired, "locked after 5 failures even with right key")

    // просрочен
    o = offer(-1)
    ck(ph.pair(qr(o), 0) is PairOutcome.Expired, "expired offer")

    // отказ
    o = offer(); decision = false
    ck(ph.pair(qr(o), 0) is PairOutcome.Rejected, "rejected by user")
    ck(tvStore.all().isEmpty() && phStore.all().isEmpty(), "nothing stored after reject")
    ck(ph.pair(qr(o), 0) is PairOutcome.Expired, "offer burned after reject")

    // успех
    o = offer(); decision = true
    val res = ph.pair(qr(o), 4444)
    ck(res is PairOutcome.Paired, "paired: $res")
    ck(tvStore.get(phId.idHex)!!.key.contentEquals(phStore.get(tvId.idHex)!!.key), "same pair key both sides")
    ck(tvStore.get(phId.idHex)!!.port == 4444 && tvStore.get(phId.idHex)!!.host == "127.0.0.1", "tv stored phone address")
    ck(ph.pair(qr(o), 0) is PairOutcome.Expired, "offer one-time")

    // sync: разные данные
    val sc = AtomicLong(3_000_000_000_000L)
    fun chg(acc: MemAccess, f: (UserRegistry) -> UserRegistry) { val n = f(acc.reg); acc.reg = RegistryKeys.stampLocalChange(acc.reg, n, sc.incrementAndGet()) }
    chg(phAcc) { it.copy(likedVideos = listOf(sr(1))) }
    chg(tvAcc) { it.copy(likedVideos = listOf(sr(2)), watchHistory = listOf(WatchHistoryItem("h", sc.incrementAndGet(), 7, 10))) }
    val peerTv = phStore.get(tvId.idHex)!!
    val r1 = ph.syncOnce(peerTv, "127.0.0.1", port)
    ck(r1 is SyncOutcome.Merged, "first sync merged: $r1")
    ck(hash(phAcc.reg) == hash(tvAcc.reg), "states equal after sync")
    ck(phAcc.reg.likedVideos.size == 2 && phAcc.reg.watchHistory.size == 1, "data on phone")
    val r2 = ph.syncOnce(phStore.get(tvId.idHex)!!, "127.0.0.1", port)
    ck(r2 is SyncOutcome.Same, "second sync same: $r2")

    // удаление доходит
    chg(tvAcc) { it.copy(likedVideos = it.likedVideos.filter { v -> v != sr(1) }) }
    ck(ph.syncOnce(phStore.get(tvId.idHex)!!, "127.0.0.1", port) is SyncOutcome.Merged, "merge after delete")
    ck(phAcc.reg.likedVideos == listOf(sr(2)), "delete reached phone")

    // большой реестр (500 истории, ~300 КБ)
    chg(phAcc) { it.copy(watchHistory = (0 until 500).map { i -> WatchHistoryItem("big$i", sc.incrementAndGet(), 1, 2, title = "x".repeat(400)) }) }
    val t0 = System.nanoTime()
    ck(ph.syncOnce(phStore.get(tvId.idHex)!!, "127.0.0.1", port) is SyncOutcome.Merged, "big merge")
    println("big sync ms=" + (System.nanoTime() - t0) / 1_000_000)
    ck(tvAcc.reg.watchHistory.size == 500, "big arrived")

    // чужой (не сопряжён) получает молчание
    val stranger = LanClient(ident("evil"), MemStore(), MemAccess(UserRegistry(lastSynced = 0)))
    val fake = LanPeer(tvId.idHex, "x", LanCrypto.randomBytes(32))
    ck(stranger.syncOnce(fake, "127.0.0.1", port) is SyncOutcome.Forgotten, "stranger rejected")
    // свой, но с неверным ключом
    val bad = phStore.get(tvId.idHex)!!.copy(key = LanCrypto.randomBytes(32))
    ck(ph.syncOnce(bad, "127.0.0.1", port) is SyncOutcome.KeyMismatch, "wrong pair key")
    // недоступный порт
    ck(ph.syncOnce(peerTv, "127.0.0.1", 1) is SyncOutcome.Unreachable, "unreachable")

    // отзыв
    ph.unpair(phStore.get(tvId.idHex)!!, "127.0.0.1", port)
    ck(phStore.all().isEmpty() && tvStore.all().isEmpty(), "unpair both sides")
    ck(ph.syncOnce(peerTv, "127.0.0.1", port) is SyncOutcome.Forgotten, "after unpair forgotten")
    tv.stop()
}


private fun testAppSettingsGenreSync() {
    val regB = UserRegistry(
        appSettings = AppSettings(enabledGenres = listOf("комедии", "аниме"), updatedAt = 1000L),
        lastSynced = 2000L
    )
    val regA = UserRegistry(
        appSettings = AppSettings(enabledGenres = listOf("комедии"), updatedAt = 3000L),
        lastSynced = 1500L
    )
    val remoteSettingsTime = if (regA.appSettings.updatedAt > 0L) regA.appSettings.updatedAt else regA.lastSynced
    val localSettingsTime = if (regB.appSettings.updatedAt > 0L) regB.appSettings.updatedAt else regB.lastSynced
    val isRemoteSettingsNewer = remoteSettingsTime > localSettingsTime

    ck(isRemoteSettingsNewer, "device A settings are newer despite device B having newer watch history")
    val baseSettings = if (isRemoteSettingsNewer) regA.appSettings else regB.appSettings
    ck(baseSettings.enabledGenres == listOf("комедии"), "genre removal on device A preserved")
}

class LanCoreTest {
    @Test fun crypto_and_qr() = testCrypto()
    @Test fun merge_basics() = testMergeBasics()
    @Test fun merge_random_convergence() = testMergeRandom()
    @Test fun pairing_and_sync_over_loopback() = testProtocol()
    @Test fun settings_genre_sync() = testAppSettingsGenreSync()
}
