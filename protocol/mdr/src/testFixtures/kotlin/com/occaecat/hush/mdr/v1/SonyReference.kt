package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.Table
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader

/**
 * Sound Connect's own v1 message classes, loaded from tools/sonyref/sound-connect.jar (made by make-jar.sh, never
 * committed). Each opcode's class reads a payload the way the app does; tests use that as the reference for ours.
 *
 * Table 1 (tm0.*, Command.mPayloadClass) parses into a struct and writes it back, so a payload Sony reads and
 * writes back unchanged has the layout Sony expects. Table 2 (an0/bn0.*, Command.mFactory) keeps the raw bytes and
 * only validates them.
 */
class SonyReference private constructor(private val loader: ClassLoader) {

    sealed interface Result {
        /** Sony read it; [rewritten] is what it writes back (table 1), or null where it keeps the bytes (table 2). */
        data class Read(val rewritten: ByteArray?) : Result
        data class Rejected(val why: String) : Result
    }

    private val t1 = loader.loadClass("com.sony.songpal.tandemfamily.message.mdr.v1.table1.Command")
    private val t2 = loader.loadClass("com.sony.songpal.tandemfamily.message.mdr.v1.table2.Command")
    private val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    private val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)

    private fun command(enum: Class<*>, code: Int): Any? = enum.enumConstants.firstOrNull {
        (enum.getMethod("byteCode").invoke(it) as Byte).toInt() and 0xFF == code
    }

    fun commandName(table: Table, code: Int): String? = command(if (table == Table.ONE) t1 else t2, code)?.toString()

    fun read(table: Table, payload: ByteArray): Result = try {
        if (table == Table.ONE) readT1(payload) else readT2(payload)
    } catch (e: InvocationTargetException) {
        Result.Rejected(e.targetException.toString())
    } catch (e: RuntimeException) {
        Result.Rejected(e.toString())
    }

    private fun readT1(payload: ByteArray): Result {
        val cmd = command(t1, payload[0].toInt() and 0xFF) ?: return Result.Rejected("unknown command")
        val cls = t1.getField("mPayloadClass").get(cmd) as Class<*>
        // The no-argument constructor sets up lists the parser fills; without one, skip constructors altogether.
        val msg = runCatching { cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance() }.getOrNull()
            ?: allocate.invoke(unsafe, cls)
        method(cls, "g", ByteArray::class.java).invoke(msg, payload)
        val out = method(cls, "f").invoke(msg) as ByteArrayOutputStream
        // allocateInstance skips the constructor that stores the command byte: put it back.
        return Result.Read(out.toByteArray().also { if (it.isNotEmpty()) it[0] = payload[0] })
    }

    /** The name of the inquired-type enum Sony read from a table-1 [payload] (its first enum field), or null. */
    fun inquiredType(payload: ByteArray): String? = runCatching {
        val cmd = command(t1, payload[0].toInt() and 0xFF) ?: return null
        val cls = t1.getField("mPayloadClass").get(cmd) as Class<*>
        val msg = runCatching { cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance() }.getOrNull()
            ?: allocate.invoke(unsafe, cls)
        method(cls, "g", ByteArray::class.java).invoke(msg, payload)
        cls.declaredFields.firstOrNull { it.type.isEnum }?.apply { isAccessible = true }?.get(msg)?.toString()
    }.getOrNull()

    private fun readT2(payload: ByteArray): Result {
        val cmd = command(t2, payload[0].toInt() and 0xFF) ?: return Result.Rejected("unknown command")
        val factory = t2.getField("mFactory").get(cmd)
        val valid = method(factory.javaClass, "a", ByteArray::class.java).invoke(factory, payload) as Boolean
        if (!valid) return Result.Rejected("factory rejected")
        method(factory.javaClass, "b", ByteArray::class.java).invoke(factory, payload)
        return Result.Read(null)
    }

    /** Sony's enum constant [name] of [cls]. */
    fun enum(cls: String, name: String): Any = loader.loadClass(cls).enumConstants.first { (it as Enum<*>).name == name }

    /** A Sony object made with its public constructor that takes exactly these arguments. */
    fun new(cls: String, vararg args: Any): Any = loader.loadClass(cls).constructors.first { c ->
        c.parameterCount == args.size && c.parameterTypes.zip(args).all { (t, a) -> t.isInstance(a) || t == Int::class.javaPrimitiveType && a is Int }
    }.newInstance(*args)

    /** What a Sony message object sends: `rm0.b.d()`. */
    fun bytes(message: Any): ByteArray = method(message.javaClass, "d").invoke(message) as ByteArray

    /** Calls the public method [name] taking [args] on [target]. */
    fun call(target: Any, name: String, vararg args: Any): Any? =
        target.javaClass.methods.first { it.name == name && it.parameterCount == args.size }.invoke(target, *args)

    /** A Kotlin `object`: the class's own static instance (jadx renames the field, so it is found by type). */
    fun instanceOf(cls: String): Any = loader.loadClass(cls).let { c ->
        c.declaredFields.first { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == c }.apply { isAccessible = true }.get(null)
    }

    private fun method(cls: Class<*>, name: String, vararg params: Class<*>) =
        generateSequence(cls) { it.superclass }.firstNotNullOf { c ->
            runCatching { c.getDeclaredMethod(name, *params) }.getOrNull()
        }.apply { isAccessible = true }

    companion object {
        private val jar = listOf("../../tools/sonyref/sound-connect.jar", "../tools/sonyref/sound-connect.jar", "tools/sonyref/sound-connect.jar").map(::File).firstOrNull { it.isFile }

        /** Null when the jar was not made: the reference tests then skip. */
        val instance: SonyReference? by lazy {
            jar?.let { SonyReference(URLClassLoader(arrayOf(it.toURI().toURL()), null)) }
        }
    }
}
