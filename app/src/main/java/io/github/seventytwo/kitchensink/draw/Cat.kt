// A black cat who sleeps above the Recent column and every so often wakes,
// stretches, perhaps wanders to another spot, finds something to do (a
// wash, a scratch, a slow blink at you, a pounce, kneading her bed) and
// curls up again. Some days a bowl of milk slides in, or a toy mouse. A
// port of the Windows client's cat.cpp: where she is and how she's lying
// at any moment is worked out here, and she's drawn through [Shapes].
package io.github.seventytwo.kitchensink.draw

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val Pi = PI.toFloat()

private fun lerp(a: Vec2, b: Vec2, k: Float) = Vec2(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
private fun lerpf(a: Float, b: Float, k: Float) = a + (b - a) * k
private fun smooth(k: Float): Float {
    val c = k.coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
}
private fun rotate(v: Vec2, a: Float) = Vec2(v.x * cos(a) - v.y * sin(a), v.x * sin(a) + v.y * cos(a))

/** Rises from 0 to 1 and back over k from 0 to 1, smoothly. */
private fun bump(k: Float) = sin(Pi * k.coerceIn(0f, 1f))

/**
 * Her shape, side on, facing right, in units with the floor at y = 0 below
 * her middle and y growing downwards. Her body is three circles, chest to
 * hips, joined smoothly. Legs: front near, front far, back near, back far.
 */
class CatPose(
    var chest: Vec2, var rChest: Float,
    var mid: Vec2, var rMid: Float,
    var hips: Vec2, var rHips: Float,
    var head: Vec2, var rHead: Float = 8f,
    /** Radians, clockwise: down towards her chest. */
    var headTilt: Float,
    val joint: Array<Vec2>,
    val paw: Array<Vec2>,
    /** From the base out. */
    val tail: Array<Vec2>,
    /** 0 closed to 1 open. */
    var eyes: Float,
    var yawn: Float,
    /** 0 to 1, licking. */
    var tongue: Float = 0f,
) {
    fun copy() = CatPose(chest, rChest, mid, rMid, hips, rHips, head, rHead, headTilt, joint.copyOf(), paw.copyOf(), tail.copyOf(), eyes, yawn, tongue)
}

/** What she's doing, for tests and anything that wants to know. */
enum class CatAction { Sleep, Stir, Wake, Stretch, Turn, Walk, Stand, Groom, Scratch, Blink, Pounce, Knead, Drink, Hunt, LieDown }

/**
 * Something on the floor with her: where along it, how far off it (a toy in
 * the air), how much it shows (0 hidden, fading in and out), its tip in
 * radians, and for a bowl how full it is.
 */
data class CatProp(val x: Float = 0f, val lift: Float = 0f, val alpha: Float = 0f, val rot: Float = 0f, val fill: Float = 0f)

/**
 * Her at one moment: her pose, where she is along the floor, which way she
 * faces (1 right, -1 left), how far she's turned round from there (0 to 1,
 * her head leading, towards the viewer), how asleep she is (1 fast asleep,
 * for her breathing and Z's), how far off the floor mid-pounce, how far her
 * head's turned to look at you, and the bowl and toy.
 */
class Cat(
    var pose: CatPose,
    var x: Float = 0f,
    var facing: Float = 1f,
    var turn: Float = 0f,
    var asleep: Float = 1f,
    var lift: Float = 0f,
    var look: Float = 0f,
    var action: CatAction = CatAction.Sleep,
    var bowl: CatProp = CatProp(),
    var toy: CatProp = CatProp(),
)

private fun v(vararg xy: Float) = xy.toList().chunked(2).map { Vec2(it[0], it[1]) }.toTypedArray()

private fun make(
    chest: Vec2, rc: Float, mid: Vec2, rm: Float, hips: Vec2, rh: Float, head: Vec2, tilt: Float,
    joint: Array<Vec2>, paw: Array<Vec2>, tail: Array<Vec2>, eyes: Float, yawn: Float,
) = CatPose(chest, rc, mid, rm, hips, rh, head, 8f, tilt, joint, paw, tail, eyes, yawn)

// Her poses, facing right.
private val Stand = make(
    Vec2(13f, -22f), 9.5f, Vec2(0f, -23f), 9f, Vec2(-13f, -22f), 10f, Vec2(25f, -31f), 0f,
    v(14f, -16f, 11f, -16f, -13f, -15f, -16f, -15f), v(15f, 0f, 11f, 0f, -12f, 0f, -17f, 0f),
    v(-22f, -25f, -27f, -29f, -30f, -35f, -31f, -42f, -30f, -48f, -27f, -53f, -23f, -55f, -20f, -54f), 1f, 0f,
)
/** Curled up asleep: head down on her paws, tail wrapped round the front. */
private val Sleep = make(
    Vec2(6f, -10f), 10f, Vec2(-4f, -12f), 11.5f, Vec2(-14f, -10f), 10.5f, Vec2(19f, -7.5f), 0.25f,
    v(10f, -4f, 8f, -4f, -12f, -4f, -15f, -4f), v(15f, -1f, 12f, -1f, -8f, -1f, -11f, -1f),
    v(-23f, -9f, -25f, -4f, -21f, -1f, -13f, 0f, -4f, 0f, 4f, 0f, 11f, -1f, 16f, -2f), 0f, 0f,
)
/** The front stretch: chest down, paws reaching, bottom and tail up, yawning. */
private val Bow = make(
    Vec2(16f, -11f), 8.5f, Vec2(3f, -17f), 8.5f, Vec2(-12f, -25f), 9.5f, Vec2(29f, -14f), -0.25f,
    v(17f, -7f, 15f, -7f, -12f, -18f, -15f, -18f), v(32f, 0f, 29f, 0f, -11f, 0f, -15f, 0f),
    v(-21f, -28f, -25f, -34f, -27f, -41f, -27f, -48f, -25f, -54f, -22f, -58f, -18f, -60f, -15f, -59f), 0f, 1f,
)
/** The arch: back up, legs together. */
private val Arch = make(
    Vec2(12f, -26f), 8.5f, Vec2(0f, -33f), 9f, Vec2(-12f, -26f), 9.5f, Vec2(22f, -30f), 0.3f,
    v(12f, -19f, 10f, -19f, -11f, -19f, -13f, -19f), v(8f, 0f, 6f, 0f, -7f, 0f, -9f, 0f),
    v(-20f, -29f, -24f, -35f, -26f, -42f, -26f, -48f, -24f, -54f, -21f, -58f, -17f, -60f, -14f, -59f), 0.6f, 0f,
)
/** Sitting up, tail curled round her front paws. */
private val Sit = make(
    Vec2(5f, -25f), 8.5f, Vec2(0f, -17f), 9.5f, Vec2(-6f, -10f), 10.5f, Vec2(9f, -38f), 0f,
    v(8f, -18f, 6f, -18f, -1f, -7f, -3f, -7f), v(9f, 0f, 6f, 0f, 6f, -1f, 4f, -1f),
    v(-15f, -6f, -18f, -3f, -16f, 0f, -10f, 0f, -3f, 0f, 4f, 0f, 10f, -1f, 14f, -2f), 1f, 0f,
)
/** Low and ready to spring: chest down, eyes wide, tail out straight. */
private val Crouch = make(
    Vec2(14f, -9f), 8.5f, Vec2(2f, -11f), 9f, Vec2(-11f, -13f), 9.5f, Vec2(25f, -13f), -0.1f,
    v(16f, -5f, 13f, -5f, -14f, -8f, -16f, -8f), v(19f, 0f, 16f, 0f, -8f, 0f, -10f, 0f),
    v(-20f, -14f, -26f, -13f, -31f, -12f, -36f, -11f, -40f, -10f, -43f, -10f, -45f, -11f, -46f, -13f), 1f, 0f,
)
/** Mid-pounce: stretched out, front paws reaching, back legs pushing off. */
private val Leap = make(
    Vec2(16f, -26f), 8.5f, Vec2(2f, -24f), 8.5f, Vec2(-12f, -22f), 9f, Vec2(28f, -30f), -0.1f,
    v(22f, -22f, 20f, -21f, -15f, -18f, -17f, -18f), v(30f, -16f, 28f, -15f, -24f, -10f, -25f, -9f),
    v(-21f, -24f, -27f, -25f, -33f, -26f, -38f, -27f, -43f, -27f, -47f, -26f, -50f, -25f, -52f, -24f), 1f, 0f,
)
/** Head down in a bowl in front of her, lapping. */
private val Drink = make(
    Vec2(14f, -11f), 8.5f, Vec2(2f, -14f), 9f, Vec2(-11f, -18f), 9.5f, Vec2(27f, -8f), 0.55f,
    v(15f, -7f, 12f, -7f, -12f, -12f, -14f, -12f), v(17f, 0f, 14f, 0f, -11f, 0f, -15f, 0f),
    v(-21f, -20f, -26f, -23f, -29f, -28f, -30f, -34f, -29f, -39f, -26f, -43f, -22f, -45f, -19f, -44f), 0.4f, 0f,
)

/** A pose with its head moved, and its eyes as given. */
private fun withHead(p: CatPose, head: Vec2, tilt: Float, eyes: Float) = p.copy().apply { this.head = head; headTilt = tilt; this.eyes = eyes }

/** Curled up, but her head raised drowsily. */
private val SleepUp = withHead(Sleep, Vec2(17f, -16f), -0.15f, 0.45f)
/** Sitting with her near front paw up at her mouth, to lick it. */
private val SitPawUp = withHead(Sit, Vec2(9f, -38f), 0.25f, 0.3f).apply { joint[0] = Vec2(16f, -25f); paw[0] = Vec2(20f, -34f) }
/** Sitting with her near back leg up behind her ear, to scratch it. */
private val SitScratch = withHead(Sit, Vec2(10f, -37f), -0.45f, 0.25f).apply { joint[2] = Vec2(-10f, -19f); paw[2] = Vec2(2f, -43f) }
/** Crouched with her front paws pinning something ahead of her. */
private val Pin = withHead(Crouch, Vec2(27f, -11f), 0.25f, 1f).apply {
    joint[0] = Vec2(20f, -6f); joint[1] = Vec2(18f, -6f)
    paw[0] = Vec2(25f, 0f); paw[1] = Vec2(23f, 0f)
}
/** The same, batting it on with her near paw. */
private val Bat = Pin.copy().apply { joint[0] = Vec2(21f, -12f); paw[0] = Vec2(29f, -7f) }

private fun blend(a: CatPose, b: CatPose, k: Float) = CatPose(
    lerp(a.chest, b.chest, k), lerpf(a.rChest, b.rChest, k),
    lerp(a.mid, b.mid, k), lerpf(a.rMid, b.rMid, k),
    lerp(a.hips, b.hips, k), lerpf(a.rHips, b.rHips, k),
    lerp(a.head, b.head, k), lerpf(a.rHead, b.rHead, k), lerpf(a.headTilt, b.headTilt, k),
    Array(4) { lerp(a.joint[it], b.joint[it], k) },
    Array(4) { lerp(a.paw[it], b.paw[it], k) },
    Array(a.tail.size) { lerp(a.tail[it], b.tail[it], k) },
    lerpf(a.eyes, b.eyes, k), lerpf(a.yawn, b.yawn, k), lerpf(a.tongue, b.tongue, k),
)

private fun hash(x0: Int): Int {
    var x = x0
    x = x xor (x ushr 16)
    x *= 0x7feb352d
    x = x xor (x ushr 15)
    x *= 0x846ca68b.toInt()
    x = x xor (x ushr 16)
    return x
}

/** Where she settles at the end of episode [k], from -1 to 1 across the floor. */
private fun spot(k: Long): Float = Integer.remainderUnsigned(hash(k.toInt() * 2654435761L.toInt()), 2001) / 1000f - 1
private fun sign(v: Float) = if (v < 0) -1f else 1f
private fun mod(h: Int, n: Int) = Integer.remainderUnsigned(h, n)

// Lengths of each part of waking up, and of her activities, in seconds.
private const val Wake = 1.4f
private const val ToBow = 0.8f
private const val BowHold = 1.4f
private const val ToArch = 0.9f
private const val ArchHold = 0.9f
private const val ToStand = 0.7f
private const val Turn = 1.3f
private const val Pause = 0.5f
private const val LieDown = 1.8f
private const val ToSit = 0.8f
private const val PawUp = 0.5f
private const val Lick = 1.8f
private const val Wash = 1.6f
private const val LegUp = 0.5f
private const val Scratch = 1.6f
private const val LookRound = 0.7f
private const val LookHold = 0.5f
private const val SlowBlink = 1.4f
private const val LookBack = 0.6f
private const val ToCrouch = 0.6f
private const val Wiggle = 1.5f
private const val LeapTime = 1.0f
private const val Land = 0.4f
private const val Knead = 3.2f
private const val StirUp = 0.7f
private const val StirLook = 1.8f
private const val StirDown = 0.9f
private const val SlideIn = 1.8f
private const val Lower = 0.75f
private const val Lap = 4.0f
private const val LickLips = 0.8f
private const val SlideOut = 1.4f
private const val RollIn = 1.6f
private const val PinDown = 0.8f
private const val BatTime = 0.8f
private const val Proud = 1.0f
private const val Speed = 22f // units a second, walking
private const val Stride = 20f // units a step cycle
private const val MinWalk = 10f // closer than this, she doesn't bother
private const val Pounce = 18f // units forward
private const val BowlAt = 31f // from her middle to the bowl's, under her mouth
private const val ToyAt = 45f // from her middle to the toy's before she pounces: just past her paws once she lands
private const val BatBy = 5f // how far each bat sends the toy

/** She sleeps for most of each episode, in seconds. */
const val CatEpisode = 100.0

private fun lengthOf(a: CatAction) = when (a) {
    CatAction.Groom -> ToSit + PawUp + Lick + Wash + PawUp + ToSit
    CatAction.Scratch -> ToSit + LegUp + Scratch + LegUp + ToSit
    CatAction.Blink -> ToSit + LookRound + LookHold + SlowBlink + LookHold + LookBack + ToSit
    CatAction.Pounce -> ToCrouch + Wiggle + LeapTime + Land + ToCrouch
    CatAction.Drink -> SlideIn + 2 * Lower + Lap + LickLips + SlideOut
    CatAction.Hunt -> RollIn + ToCrouch + Wiggle + LeapTime + PinDown + 2 * BatTime + ToSit + Proud + ToSit
    else -> 0f
}

/** Moves her legs, body and tail for a step cycle at [phase]. */
private fun walking(p: CatPose, phase: Float, t: Double) {
    val offsets = floatArrayOf(0f, Pi, Pi, 0f) // diagonal pairs together
    for (i in 0 until 4) {
        val a = phase + offsets[i]
        p.paw[i] = Vec2(p.paw[i].x + 6 * sin(a), p.paw[i].y - max(0f, cos(a)) * 4)
    }
    val bob = 0.8f * sin(2 * phase)
    p.chest += Vec2(0f, bob); p.mid += Vec2(0f, bob); p.hips += Vec2(0f, bob); p.head += Vec2(0f, bob * 0.7f)
    for (i in p.tail.indices) p.tail[i] = Vec2(p.tail[i].x + sin(t.toFloat() * 2.2f + i * 0.5f) * i * 0.35f, p.tail[i].y)
}

/** Her sides rise and fall, and now and then her tail tip twitches in her sleep. */
private fun breathing(p: CatPose, t: Double, asleep: Float) {
    val b = sin(2 * Pi * t.toFloat() / 3.6f) * asleep
    p.rMid += 0.7f * b; p.mid = Vec2(p.mid.x, p.mid.y - 0.4f * b)
    p.rChest += 0.3f * b
    val twitch = (t % 7.3).toFloat()
    if (twitch < 0.8f) {
        val k = sin(twitch / 0.8f * Pi) * asleep
        for (i in 5 until p.tail.size) p.tail[i] = Vec2(p.tail[i].x, p.tail[i].y - k * (i - 4) * 1.5f)
    }
}

/** The bum wiggle and tail flick before she springs. */
private fun wiggle(p: CatPose, t: Float) {
    val w = sin(2 * Pi * 4 * t)
    p.hips = Vec2(p.hips.x + 1.2f * w, p.hips.y - 0.6f * abs(w))
    for (i in 5 until p.tail.size) p.tail[i] = Vec2(p.tail[i].x, p.tail[i].y - w * (i - 4) * 1.2f)
}

/** Where she settles at the end of an episode, and which way she faces there. */
private data class Rest(val x: Float, val facing: Float)

/** What she does in one episode: where she starts and whether she walks, what she does there, and where she ends up. */
private class Plan(
    val from: Float, val to: Float, val facing: Float, val walkTo: Float, val walks: Boolean,
    val walk: Float, val turn: Float, val doings: List<CatAction>, val knead: Boolean,
    val stir: Boolean, val stirAt: Float, val end: Rest,
)

/** Episode [k] from where the last left her. Everything comes from k's hash, so it's the same whenever it's asked. */
private fun plan(k: Long, rest: Rest, half: Float): Plan {
    val room = max(0f, half - 46) // keep all of her on the floor, nose to tail
    val to = spot(k) * room
    val walkTo = sign(to - rest.x)
    val walks = abs(to - rest.x) >= MinWalk
    var x = if (walks) to else rest.x
    val dir = if (walks) walkTo else rest.facing

    val h = hash(k.toInt() * 0x9E3779B9L.toInt() + 7)
    val choices = listOf(CatAction.Groom, CatAction.Scratch, CatAction.Blink, CatAction.Pounce, CatAction.Drink, CatAction.Hunt)
    val first = mod(h, 6)
    val second = (first + 1 + mod(h ushr 4, 5)) % 6
    val count = if (mod(h ushr 8, 3) == 0) 2 else 1
    val doings = listOf(choices[first], choices[second]).take(count).map { chosen ->
        // Each only with room on the floor for it: to land a pounce, for the
        // bowl in front of her, for the toy to skid along.
        val lands = room > 0 && abs(x + dir * Pounce) <= room
        var a = chosen
        if (a == CatAction.Pounce && !lands) a = CatAction.Blink
        if (a == CatAction.Drink && abs(x + dir * BowlAt) > half - 10) a = CatAction.Groom
        if (a == CatAction.Hunt && (!lands || abs(x + dir * (ToyAt + Pounce + 2 * BatBy)) > half - 11)) a = CatAction.Scratch
        if (a == CatAction.Pounce || a == CatAction.Hunt) x += dir * Pounce
        a
    }
    return Plan(
        rest.x, to, rest.facing, walkTo, walks,
        walk = if (walks) abs(to - rest.x) / Speed else 0f,
        turn = if (walks && walkTo != rest.facing) Turn else 0f,
        doings = doings, knead = mod(h ushr 12, 2) == 0, stir = mod(h ushr 16, 5) < 2,
        stirAt = 15f + mod(h ushr 20, 40), end = Rest(x, dir),
    )
}

/**
 * Where she is after episode [k], played from the first: where she is and
 * which way she faces depend on every episode before. The last answer is
 * kept, so each frame takes a step at most.
 */
private object Rests {
    private var lastK = -2L
    private var lastHalf = -1f
    private var last = Rest(0f, 1f)

    @Synchronized
    fun after(k: Long, half: Float): Rest {
        var from = -1L
        var r = Rest(spot(-1) * max(0f, half - 46), 1f)
        if (half == lastHalf && lastK >= -1 && lastK <= k) {
            from = lastK
            r = last
        }
        for (j in from + 1..k) r = plan(j, r, half).end
        lastK = k
        lastHalf = half
        last = r
        return r
    }
}

/** Steps through an episode's parts: [take] says whether the time falls in the next part, of len seconds, and [k] how far into it. */
private class Timeline(var at: Float) {
    var k = 0f
    fun take(len: Float): Boolean {
        if (len <= 0) return false
        if (at < len) {
            k = at / len
            return true
        }
        at -= len
        return false
    }
}

/**
 * Her pose partway through an activity, which starts and ends standing
 * where she is; [dir] is the way she faces. It returns false once the
 * activity's over, leaving her moved on by a pounce.
 */
private fun doing(a: CatAction, tl: Timeline, t: Double, dir: Float, half: Float, c: Cat): Boolean {
    val ft = t.toFloat()
    fun sitDown() = tl.take(ToSit).also { if (it) c.pose = blend(Stand, Sit, smooth(tl.k)) }
    fun standUp() = tl.take(ToSit).also { if (it) c.pose = blend(Sit, Stand, smooth(tl.k)) }
    when (a) {
        CatAction.Groom -> {
            if (sitDown()) return true
            if (tl.take(PawUp)) return true.also { c.pose = blend(Sit, SitPawUp, smooth(tl.k)) }
            if (tl.take(Lick)) {
                // Quick licks, her head bobbing with them.
                c.pose = SitPawUp.copy()
                c.pose.tongue = 0.5f + 0.5f * sin(2 * Pi * 3 * ft)
                c.pose.headTilt += 0.06f * sin(2 * Pi * 3 * ft)
                return true
            }
            if (tl.take(Wash)) {
                // Two strokes of the paw up over her face to her ear and down.
                c.pose = SitPawUp.copy()
                val up = sin(2 * Pi * tl.k).pow(2)
                c.pose.paw[0] = Vec2(20 - 5 * up, -34 - 12 * up)
                c.pose.joint[0] = Vec2(16 - up, -25 - 6 * up)
                c.pose.headTilt = 0.35f + 0.1f * up
                return true
            }
            if (tl.take(PawUp)) return true.also { c.pose = blend(SitPawUp, Sit, smooth(tl.k)) }
            return standUp()
        }
        CatAction.Scratch -> {
            if (sitDown()) return true
            if (tl.take(LegUp)) return true.also { c.pose = blend(Sit, SitScratch, smooth(tl.k)) }
            if (tl.take(Scratch)) {
                c.pose = SitScratch.copy()
                val s = sin(2 * Pi * 7 * ft)
                c.pose.paw[2] = c.pose.paw[2] + Vec2(0.8f * s, 1.6f * s)
                c.pose.joint[2] = c.pose.joint[2] + Vec2(0f, 0.8f * s)
                return true
            }
            if (tl.take(LegUp)) return true.also { c.pose = blend(SitScratch, Sit, smooth(tl.k)) }
            return standUp()
        }
        CatAction.Blink -> {
            // She looks round at you and slowly blinks: a cat's way of saying
            // she likes you.
            if (sitDown()) return true
            c.pose = Sit.copy()
            if (tl.take(LookRound)) return true.also { c.look = smooth(tl.k) }
            c.look = 1f
            if (tl.take(LookHold)) return true
            if (tl.take(SlowBlink)) return true.also { c.pose.eyes = 1 - smooth(bump(tl.k) * 1.4f) }
            if (tl.take(LookHold)) return true
            if (tl.take(LookBack)) return true.also { c.look = 1 - smooth(tl.k) }
            c.look = 0f
            return standUp()
        }
        CatAction.Pounce -> {
            if (tl.take(ToCrouch)) return true.also { c.pose = blend(Stand, Crouch, smooth(tl.k)) }
            if (tl.take(Wiggle)) return true.also { c.pose = Crouch.copy().also { wiggle(it, ft) } }
            if (tl.take(LeapTime)) {
                val k = tl.k
                c.pose = if (k < 0.5f) blend(Crouch, Leap, smooth(k * 2)) else blend(Leap, Crouch, smooth(k * 2 - 1))
                c.x += dir * Pounce * smooth(k)
                c.lift = 10 * bump(k)
                return true
            }
            c.x += dir * Pounce
            if (tl.take(Land)) return true.also { c.pose = Crouch.copy() }
            if (tl.take(ToCrouch)) return true.also { c.pose = blend(Crouch, Stand, smooth(tl.k)) }
            return false
        }
        CatAction.Drink -> {
            // A bowl of milk slides in from her side of the floor and stops
            // under her nose; she laps it, licks her lips, and off it goes.
            val edge = dir * (half - 10)
            val at = c.x + dir * BowlAt
            c.bowl = CatProp(x = at, alpha = 1f, fill = 1f)
            if (tl.take(SlideIn)) {
                c.pose = Stand.copy()
                c.bowl = c.bowl.copy(x = lerpf(edge, at, smooth(tl.k)), alpha = min(1f, tl.k * 2.5f))
                return true
            }
            if (tl.take(Lower)) return true.also { c.pose = blend(Stand, Drink, smooth(tl.k)) }
            if (tl.take(Lap)) {
                c.pose = Drink.copy()
                val lap = sin(2 * Pi * 4 * ft)
                c.pose.tongue = 0.5f + 0.5f * lap
                c.pose.head = c.pose.head + Vec2(0f, 0.6f * lap)
                c.bowl = c.bowl.copy(fill = 1 - 0.75f * tl.k)
                return true
            }
            c.bowl = c.bowl.copy(fill = 0.25f)
            if (tl.take(Lower)) return true.also { c.pose = blend(Drink, Stand, smooth(tl.k)) }
            if (tl.take(LickLips)) {
                c.pose = Stand.copy()
                c.pose.tongue = abs(sin(2 * Pi * tl.k))
                return true
            }
            if (tl.take(SlideOut)) {
                c.pose = Stand.copy()
                c.bowl = c.bowl.copy(x = lerpf(at, edge, smooth(tl.k)), alpha = min(1f, (1 - tl.k) * 2.5f))
                return true
            }
            c.bowl = CatProp()
            return false
        }
        CatAction.Hunt -> {
            // A toy mouse skids in ahead of her. She stalks it, pounces on it,
            // bats it along twice, then sits back pleased while it goes.
            val start = c.x
            val toyAt = start + dir * ToyAt
            c.toy = CatProp(x = toyAt, alpha = 1f)
            if (tl.take(RollIn)) {
                c.pose = Stand.copy()
                val k = tl.k
                // Wobbling as it skids.
                c.toy = CatProp(x = lerpf(dir * (half - 11), toyAt, smooth(k)), rot = 0.45f * sin(4 * Pi * k) * (1 - k), alpha = min(1f, k * 3))
                return true
            }
            if (tl.take(ToCrouch)) return true.also { c.pose = blend(Stand, Crouch, smooth(tl.k)) }
            if (tl.take(Wiggle)) return true.also { c.pose = Crouch.copy().also { wiggle(it, ft) } }
            if (tl.take(LeapTime)) {
                val k = tl.k
                c.pose = if (k < 0.5f) blend(Crouch, Leap, smooth(k * 2)) else blend(Leap, Pin, smooth(k * 2 - 1))
                c.x = start + dir * Pounce * smooth(k)
                c.lift = 10 * bump(k)
                return true
            }
            c.x = start + dir * Pounce
            if (tl.take(PinDown)) return true.also { c.pose = Pin.copy() }
            for (hit in 0 until 2) {
                if (tl.take(BatTime)) {
                    val k = tl.k
                    c.pose = blend(Pin, Bat, bump(k))
                    // Knocked on with a hop and a tumble once her paw lands.
                    val go = smooth((k * 2 - 0.5f).coerceIn(0f, 1f))
                    c.toy = CatProp(
                        x = toyAt + dir * BatBy * (hit + go), lift = 3 * bump((k * 2 - 0.5f).coerceIn(0f, 1f)),
                        alpha = 1f, rot = 0.45f * dir * sin(2 * Pi * go),
                    )
                    return true
                }
            }
            c.toy = CatProp(x = toyAt + dir * 2 * BatBy, alpha = 1f)
            if (tl.take(ToSit)) return true.also { c.pose = blend(Pin, Sit, smooth(tl.k)) }
            if (tl.take(Proud)) return true.also { c.pose = Sit.copy(); c.toy = c.toy.copy(alpha = 1 - smooth(tl.k)) }
            c.toy = CatProp()
            return standUp()
        }
        else -> return false
    }
}

/** The cat [t] seconds in, with [half] units of floor either side of the middle to wander. */
fun catAt(t: Double, half: Float): Cat {
    // Start some way into an episode, so she's soon up for the first time.
    val s = t + CatEpisode * 0.75
    val k = floor(s / CatEpisode).toLong()
    val u = (s - k * CatEpisode).toFloat()
    val p = plan(k, Rests.after(k - 1, half), half)

    var active = Wake + ToBow + BowHold + ToArch + ArchHold + ToStand + p.turn + p.walk + Pause + LieDown
    for (a in p.doings) active += lengthOf(a)
    if (p.knead) active += Knead
    val sleep = CatEpisode.toFloat() - active

    val c = Cat(Sleep.copy(), p.from, p.facing)
    val tl = Timeline(u)
    when {
        tl.take(sleep) -> {
            c.asleep = 1f
            // Now and then she stirs: lifts her head, looks about drowsily
            // and settles again.
            val since = tl.k * sleep - p.stirAt
            if (p.stir && since >= 0 && since < StirUp + StirLook + StirDown) {
                c.action = CatAction.Stir
                when {
                    since < StirUp -> {
                        val e = smooth(since / StirUp)
                        c.pose = blend(Sleep, SleepUp, e); c.asleep = 1 - 0.6f * e
                    }
                    since < StirUp + StirLook -> {
                        c.pose = SleepUp.copy(); c.asleep = 0.4f
                        c.pose.head = c.pose.head + Vec2(sin((since - StirUp) / StirLook * 2 * Pi), 0f)
                    }
                    else -> {
                        val e = smooth((since - StirUp - StirLook) / StirDown)
                        c.pose = blend(SleepUp, Sleep, e); c.asleep = 0.4f + 0.6f * e
                    }
                }
            }
        }
        tl.take(Wake) -> {
            c.action = CatAction.Wake
            c.pose = blend(Sleep, Stand, smooth(tl.k)); c.asleep = 1 - smooth(tl.k * 2)
        }
        else -> {
            c.asleep = 0f
            c.action = CatAction.Stretch
            when {
                tl.take(ToBow) -> c.pose = blend(Stand, Bow, smooth(tl.k))
                tl.take(BowHold) -> c.pose = Bow.copy()
                tl.take(ToArch) -> c.pose = blend(Bow, Arch, smooth(tl.k))
                tl.take(ArchHold) -> c.pose = Arch.copy()
                tl.take(ToStand) -> c.pose = blend(Arch, Stand, smooth(tl.k))
                tl.take(p.turn) -> {
                    // Turning round, towards the viewer: see drawCat.
                    c.action = CatAction.Turn
                    c.pose = Stand.copy(); c.turn = tl.k
                    walking(c.pose, 2 * Pi * tl.k, t) // stepping round
                }
                tl.take(p.walk) -> {
                    c.action = CatAction.Walk
                    c.pose = Stand.copy(); c.facing = p.walkTo
                    val gone = abs(p.to - p.from) * tl.k
                    c.x = p.from + p.walkTo * gone
                    walking(c.pose, 2 * Pi * gone / Stride, t)
                }
                else -> {
                    if (p.walks) { c.x = p.to; c.facing = p.walkTo }
                    c.pose = Stand.copy()
                    var busy = tl.take(Pause)
                    if (busy) c.action = CatAction.Stand
                    for (a in p.doings) {
                        if (busy) break
                        c.action = a
                        busy = doing(a, tl, t, c.facing, half, c)
                        if (!busy) c.lift = 0f
                    }
                    if (!busy && p.knead && tl.take(Knead)) {
                        // Making biscuits: front paws pressing in turn, eyes half shut.
                        busy = true
                        c.action = CatAction.Knead
                        c.pose = Stand.copy()
                        val w = sin(2 * Pi * 1.6f * t.toFloat())
                        c.pose.paw[0] = c.pose.paw[0] - Vec2(0f, max(0f, w) * 3)
                        c.pose.joint[0] = c.pose.joint[0] - Vec2(0f, max(0f, w) * 1.5f)
                        c.pose.paw[1] = c.pose.paw[1] - Vec2(0f, max(0f, -w) * 3)
                        c.pose.joint[1] = c.pose.joint[1] - Vec2(0f, max(0f, -w) * 1.5f)
                        c.pose.chest = c.pose.chest + Vec2(0f, 0.4f * abs(w))
                        c.pose.eyes = 0.35f
                    }
                    if (!busy) {
                        c.action = CatAction.LieDown
                        val f = min(tl.at / LieDown, 1f)
                        c.pose = blend(Stand, Sleep, smooth(f)); c.asleep = smooth(f * 1.5f - 0.5f)
                    }
                }
            }
        }
    }
    // A blink every few seconds while she's awake.
    if (c.asleep < 1 && t % 4.1 < 0.14) c.pose.eyes = 0f
    breathing(c.pose, t, c.asleep)
    return c
}

private val Fur = Color(17, 17, 21)
private val Rim = Color(92, 98, 112, 170)
private val Eye = Color(92, 168, 245)
private val Pupil = Color(8, 8, 10)
private val Lid = Color(120, 124, 136, 220)
private val Mouth = Color(176, 84, 100)
private val Tongue = Color(222, 112, 132)
private val Whisker = Color(150, 154, 166, 110)
private val Zee = Color(150, 156, 172)
private val Shadow = Color(0, 0, 0, 55)

/**
 * Maps her pose onto the canvas, mirrored for which way she faces, and
 * turned round about the vertical: pose x is how far forward a point is,
 * and l how far to her side nearer the viewer. While she turns round, her
 * head leads and her hips follow, so each point turns by an angle between
 * theirs, from how far forward it is. Her head can also turn by itself,
 * about its own middle ([pivot]), by [look] radians, to look at the viewer;
 * [head] is set while drawing it.
 */
private class Painter(
    val c: Shapes, val floor: Vec2, val x: Float, val facing: Float, val size: Float,
    var headTurn: Float = 0f, var hipsTurn: Float = 0f, var lift: Float = 0f,
    var head: Boolean = false, var pivot: Vec2 = Vec2(0f, 0f), var look: Float = 0f,
) {
    fun angle(f: Float) = lerpf(hipsTurn, headTurn, ((f + 13) / 38).coerceIn(0f, 1f))
    fun headAngle() = angle(pivot.x) + look
    fun m(p: Vec2, l: Float = 0f): Vec2 {
        val y = floor.y + (p.y - lift) * size
        if (head) {
            val a0 = angle(pivot.x)
            val a = a0 + look
            val dx = p.x - pivot.x
            return Vec2(floor.x + (x + facing * (pivot.x * cos(a0) + dx * cos(a) - l * sin(a))) * size, y)
        }
        val a = angle(p.x)
        return Vec2(floor.x + (x + facing * (p.x * cos(a) - l * sin(a))) * size, y)
    }
    /** How far towards the viewer a point is, beside a centre at forward [f0]. */
    fun depth(f: Float, l: Float, f0: Float): Float {
        val a = if (head) headAngle() else angle(f0)
        return (f - f0) * sin(a) + l * cos(a)
    }
    fun onHead() = Painter(c, floor, x, facing, size, headTurn, hipsTurn, lift, true, pivot, look)
    fun w(r: Float) = r * size
    fun circle(p: Vec2, r: Float, col: Color, l: Float = 0f) = c.circle(m(p, l), w(r), col)
    fun line(a: Vec2, b: Vec2, t: Float, col: Color, l: Float = 0f) = c.line(m(a, l), m(b, l), w(t), col)
    fun limb(a: Vec2, b: Vec2, t: Float, col: Color, l: Float = 0f) {
        line(a, b, t, col, l)
        circle(a, t / 2, col, l)
        circle(b, t / 2, col, l)
    }
    fun poly(pts: List<Vec2>, col: Color, l: Float = 0f) = c.polygon(pts.map { m(it, l) }, col)
}

/** Her body, legs, tail and head in one colour, grown by [g] units all round: once wide for her rim, then in black over it. */
private fun silhouette(d: Painter, p: CatPose, g: Float, col: Color) {
    // Far legs, then the tail, behind the body. One colour throughout, so
    // what's in front of what doesn't matter.
    d.limb(p.joint[1], p.paw[1], 4.6f + 2 * g, col, -3.5f)
    d.limb(p.joint[3], p.paw[3], 5.2f + 2 * g, col, -4f)
    val n = p.tail.size
    for (i in 0 until n - 1) d.limb(p.tail[i], p.tail[i + 1], 4.4f - 1.4f * i / (n - 1) + 2 * g, col)
    // The body: three circles and the quads joining them.
    fun join(a: Vec2, ra: Float, b: Vec2, rb: Float) {
        val dir = b - a
        val len = max(0.001f, hypot(dir.x, dir.y))
        val nrm = Vec2(-dir.y / len, dir.x / len)
        d.poly(listOf(a + nrm * (ra + g), b + nrm * (rb + g), b - nrm * (rb + g), a - nrm * (ra + g)), col)
    }
    d.circle(p.chest, p.rChest + g, col)
    d.circle(p.mid, p.rMid + g, col)
    d.circle(p.hips, p.rHips + g, col)
    join(p.chest, p.rChest, p.mid, p.rMid)
    join(p.mid, p.rMid, p.hips, p.rHips)
    d.limb(p.joint[0], p.paw[0], 4.6f + 2 * g, col, 3.5f)
    d.limb(p.joint[2], p.paw[2], 5.2f + 2 * g, col, 4f)
    // Neck, head, muzzle and ears.
    d.limb(lerp(p.chest, p.head, 0.35f), lerp(p.chest, p.head, 0.8f), 8 + 2 * g, col)
    fun head(v: Vec2) = p.head + rotate(v, p.headTilt)
    val h = d.onHead()
    h.circle(p.head, p.rHead + g, col)
    h.circle(head(Vec2(6f, 2.5f)), 4.6f + g, col)
    for (e in listOf(-3.5f, 3.0f)) {
        val l = if (e < 0) -3.4f else 3.4f // side by side, seen face on
        val base1 = head(Vec2(e - 3.6f, -5f))
        val base2 = head(Vec2(e + 3.6f, -5f))
        val tip = head(Vec2(e + 1.2f, -13.5f))
        val mid = (base1 + base2 + tip) * (1f / 3)
        fun grow(v: Vec2): Vec2 {
            val off = v - mid
            val len = max(0.001f, hypot(off.x, off.y))
            return v + off * (g / len * 1.4f)
        }
        h.poly(listOf(grow(base1), grow(tip), grow(base2)), col, l)
    }
}

private fun face(body: Painter, p: CatPose) {
    val d = body.onHead()
    fun head(v: Vec2) = p.head + rotate(v, p.headTilt)
    // Both eyes, the far one hidden behind her head unless she's turned
    // towards the viewer, and narrower the more side on each is.
    for (l in listOf(2.6f, -2.6f)) {
        val eye = head(Vec2(3.8f, -1.2f))
        val k = ((d.depth(eye.x, l, p.head.x) + 0.5f) / 3).coerceIn(0f, 1f)
        if (k <= 0) continue
        if (p.eyes > 0.15f) {
            d.c.ellipse(d.m(eye, l), d.w(1.9f * (0.5f + 0.5f * k)), d.w(2.4f * p.eyes), 0f, Eye)
            d.c.ellipse(d.m(eye, l), d.w(0.55f), d.w(2.0f * p.eyes), 0f, Pupil)
        } else {
            // Closed: a sleepy curve.
            d.line(head(Vec2(2.2f, -0.6f)), head(Vec2(3.8f, 0.2f)), 0.8f, Lid, l)
            d.line(head(Vec2(3.8f, 0.2f)), head(Vec2(5.4f, -0.6f)), 0.8f, Lid, l)
        }
    }
    if (p.yawn > 0.3f) d.c.ellipse(d.m(head(Vec2(8.5f, 4.6f))), d.w(1.4f), d.w(2.4f * p.yawn), 0f, Mouth)
    if (p.tongue > 0.05f) d.c.ellipse(d.m(head(Vec2(10.2f, 4.4f))), d.w(1.1f), d.w(1.7f * p.tongue), 0f, Tongue)
    // Whiskers both sides, fanning out, the far ones only once she's turned
    // enough to show them.
    val far = sin(d.headAngle()) > 0.25f
    for (side in listOf(1f, -1f)) {
        if (side < 0 && !far) continue
        for (dy in listOf(-0.6f, 1.0f)) {
            val a = head(Vec2(9f, 2 + dy))
            val b = head(Vec2(15f, 1 + dy * 2.2f))
            d.c.line(d.m(a, side * 1.5f), d.m(b, side * 6), d.w(0.45f), Whisker)
        }
    }
}

/** Z's float up from her head while she's asleep: a small z, rising, growing and fading, one after another. */
private fun zs(d: Painter, cat: Cat, t: Double) {
    if (cat.asleep < 0.5f) return // not once her head is up
    for (i in 0 until 3) {
        val k = ((t / 4.5 + i / 3.0) % 1.0).toFloat()
        val s = 2.2f + 2.6f * k
        val at = cat.pose.head + Vec2(4 + 9 * k, -14 - 30 * k)
        val col = Zee.copy(alpha = cat.asleep * min(k * 5, 1f) * (1 - k))
        // Z's read left to right whichever way she faces.
        val o = d.m(at)
        val w = d.w(s)
        d.c.line(Vec2(o.x - w / 2, o.y - w / 2), Vec2(o.x + w / 2, o.y - w / 2), d.w(0.9f), col)
        d.c.line(Vec2(o.x + w / 2, o.y - w / 2), Vec2(o.x - w / 2, o.y + w / 2), d.w(0.9f), col)
        d.c.line(Vec2(o.x - w / 2, o.y + w / 2), Vec2(o.x + w / 2, o.y + w / 2), d.w(0.9f), col)
    }
}

private fun Color.faded(a: Float) = copy(alpha = alpha * a.coerceIn(0f, 1f))

/** A dish of milk on the floor, its rim over her chin when she's drinking from it. */
private fun drawBowl(c: Shapes, b: CatProp, floor: Vec2, size: Float) {
    if (b.alpha <= 0) return
    val a = b.alpha
    fun at(x: Float, y: Float) = Vec2(floor.x + (b.x + x) * size, floor.y + y * size)
    c.ellipse(at(0f, 0.6f), 9 * size, 1.2f * size, 0f, Shadow.faded(a))
    c.polygon(listOf(at(-8f, -4.5f), at(8f, -4.5f), at(6f, 0f), at(-6f, 0f)), Color(196, 204, 222).faded(a))
    c.polygon(listOf(at(-7.4f, -3.2f), at(7.4f, -3.2f), at(7.1f, -2.2f), at(-7.1f, -2.2f)), Color(90, 140, 220).faded(a))
    c.ellipse(at(0f, -4.5f), 8 * size, 1.6f * size, 0f, Color(226, 232, 244).faded(a))
    // The milk, lower and narrower as she drinks it.
    if (b.fill > 0.05f) {
        val sink = (1 - b.fill) * 1.1f
        c.ellipse(at(0f, -4.5f + sink * 0.5f), (6.8f - sink) * size, (1.1f - sink * 0.3f) * size, 0f, Color(250, 250, 244).faded(a))
    }
}

/** A toy mouse, facing away from her ([dir]), tipped by its rot. */
private fun drawToy(c: Shapes, m: CatProp, dir: Float, floor: Vec2, size: Float) {
    if (m.alpha <= 0) return
    val a = m.alpha
    val mid = Vec2(m.x, -2.4f - m.lift)
    fun at(x: Float, y: Float): Vec2 {
        val r = rotate(Vec2(x * dir, y), m.rot)
        return Vec2(floor.x + (mid.x + r.x) * size, floor.y + (mid.y + r.y) * size)
    }
    if (m.lift < 4) c.ellipse(Vec2(floor.x + m.x * size, floor.y + 0.5f * size), 5 * size, 0.9f * size, 0f, Shadow.faded(a))
    val fur = Color(138, 143, 153).faded(a)
    val pink = Color(230, 140, 160).faded(a)
    c.line(at(-4f, 0.3f), at(-7f, -1.2f), 0.6f * size, pink)
    c.line(at(-7f, -1.2f), at(-9.5f, 0.6f), 0.5f * size, pink)
    c.ellipse(at(0f, 0f), 4.2f * size, 2.4f * size, m.rot, fur)
    c.circle(at(3.4f, -0.3f), 1.8f * size, fur)
    c.circle(at(2.4f, -2.2f), 1.2f * size, pink)
    c.circle(at(5.3f, 0f), 0.6f * size, pink)
    c.circle(at(4.2f, -0.6f), 0.4f * size, Pupil.faded(a))
}

/** Draws her, mapping pose units to the canvas by floor + p * size, where [floor] is the middle of the floor. [t] moves her Z's along. */
fun drawCat(c: Shapes, cat: Cat, t: Double, floor: Vec2, size: Float) {
    // The toy goes under her paws; the bowl's rim over her chin.
    drawToy(c, cat.toy, cat.facing, floor, size)
    val d = Painter(c, floor, cat.x, cat.facing, size)
    // Turning round, her head comes round first, towards the viewer, and
    // her hips follow.
    d.headTurn = Pi * smooth(cat.turn * 1.4f)
    d.hipsTurn = Pi * smooth(cat.turn * 1.4f - 0.4f)
    val across = abs(cos((d.headTurn + d.hipsTurn) / 2))
    // Her shadow stays on the floor, smaller the higher she leaps.
    val high = 1 - (cat.lift / 30).coerceIn(0f, 0.5f)
    d.c.ellipse(d.m(Vec2(0f, 0.5f)), d.w(26 * max(0.55f, across) * high), d.w((2.6f + 0.8f * (1 - across)) * high), 0f, Shadow)
    d.lift = cat.lift
    // Looking at the viewer, her head comes most of the way round.
    d.pivot = cat.pose.head
    d.look = cat.look * Pi * 0.42f
    silhouette(d, cat.pose, 1.1f, Rim)
    silhouette(d, cat.pose, 0f, Fur)
    face(d, cat.pose)
    drawBowl(c, cat.bowl, floor, size)
    zs(d, cat, t)
}
