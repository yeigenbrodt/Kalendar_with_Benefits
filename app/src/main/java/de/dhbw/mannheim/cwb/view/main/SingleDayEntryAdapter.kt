package de.dhbw.mannheim.cwb.view.main

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.dhbw.mannheim.cwb.databinding.MaterialCardButtonViewBinding
import de.dhbw.mannheim.cwb.databinding.MaterialCardViewBinding
import java.util.concurrent.Executor

class SingleDayEntryAdapter(
    private val layoutInflater: LayoutInflater
) : RecyclerView.Adapter<DayEntryViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayEntryViewHolder {
        val binding = MaterialCardViewBinding.inflate(layoutInflater, parent, false)
        return DayEntryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DayEntryViewHolder, position: Int) {
        this[position].let {
            holder.binding.title.visibility = if (it.title == null) View.GONE else View.VISIBLE
            holder.binding.title.text = it.title

            holder.binding.secondaryText.visibility =
                if (it.subtitle == null) View.GONE else View.VISIBLE
            holder.binding.secondaryText.text = it.subtitle

            holder.binding.supportingText.visibility =
                if (it.text == null) View.GONE else View.VISIBLE
            holder.binding.supportingText.text = it.text

            it.icon.let { icon ->
                holder.binding.icon.setImageDrawable(icon)
                holder.binding.icon.visibility = if (icon == null) View.GONE else View.VISIBLE
            }

            holder.binding.buttons.removeAllViews()
            holder.binding.buttons.visibility = if (it.buttons == null) View.GONE else View.VISIBLE
            it.buttons?.forEach { (key, onClick) ->
                val binding = MaterialCardButtonViewBinding.inflate(
                    layoutInflater, holder.binding.buttons, true
                )

                binding.root.text = key
                binding.root.setOnClickListener(onClick ?: it.onClick)
            }

            holder.binding.root.setOnClickListener(it.onClick)
        }
    }

    override fun getItemCount() = synchronized(lock) {
        var counter = 0
        overAllEntries { counter++ }
        counter
    }

    // ------------------------------------------ //

    private val lock: Any = Any()

    private var firstEntry: LinkedEntry? = null
    private var lastEntry: LinkedEntry? = null

    private val boundaries: MutableMap<String, LinkedEntry.Boundary> = mutableMapOf()
    val sublists: Set<String> get() = boundaries.keys

    private fun overAllEntries(block: (LinkedEntry.Value) -> Unit) = overAllEntriesWhile {
        block(
            it
        ).let { true }
    }

    private fun overAllEntriesWhile(block: (LinkedEntry.Value) -> Boolean) {
        var pointer = firstEntry
        while (pointer != null) {
            if (pointer is LinkedEntry.Value && !block(pointer)) break
            pointer = pointer.successor
        }
    }

    operator fun get(index: Int): DayEntry {
        synchronized(lock) {
            var counter = 0
            var value: DayEntry? = null
            overAllEntriesWhile {
                if (counter++ == index) value = it.value
                counter <= index
            }

            return value ?: throw IndexOutOfBoundsException()
        }
    }

    fun addSublist(index: Int, name: String) {
        if (name !in boundaries) synchronized(lock) {
            boundaries[name] =
                if (firstEntry == null) LinkedEntry.Boundary(name).also { firstEntry = it }
                    .also { lastEntry = it }
                else {
                    var counter = 0
                    var pointer = firstEntry

                    while (counter < index && pointer != null) {
                        if (pointer is LinkedEntry.Boundary) counter++
                        pointer = pointer.successor
                    }

                    if (pointer == null) LinkedEntry.Boundary(name, lastEntry)
                        .also { lastEntry!!.successor = it }.also { lastEntry = it }
                    else LinkedEntry.Boundary(name, pointer.predecessor, pointer).also {
                        if (pointer.predecessor == null) firstEntry = it
                        else pointer.predecessor!!.successor = it
                    }.also { pointer.predecessor = it }
                }
        }
    }

    fun addSublist(name: String) {
        if (name !in boundaries) synchronized(lock) {
            boundaries[name] =
                if (firstEntry == null) LinkedEntry.Boundary(name).also { firstEntry = it }
                    .also { lastEntry = it }
                else LinkedEntry.Boundary(name, lastEntry).also { lastEntry!!.successor = it }
                    .also { lastEntry = it }
        }
    }

    fun sublist(name: String): Sublist = synchronized(lock) {
        if (name !in boundaries) addSublist(name)
        val start = boundaries.getValue(name)
        Sublist(this, start)
    }

    fun sublist(name: String, block: Sublist.() -> Unit) = sublist(name).block()

    internal sealed class LinkedEntry(
        var predecessor: LinkedEntry?, var successor: LinkedEntry?
    ) {
        class Value(
            var value: DayEntry, predecessor: LinkedEntry? = null, successor: LinkedEntry? = null
        ) : LinkedEntry(predecessor, successor)

        class Boundary(
            val name: String, predecessor: LinkedEntry? = null, successor: LinkedEntry? = null
        ) : LinkedEntry(predecessor, successor)
    }

    class Sublist internal constructor(
        private val backedList: SingleDayEntryAdapter,
        private val start: LinkedEntry.Boundary,
    ) {

        private val startIndex: Int
            get() {
                var counter = 0
                var pointer = backedList.firstEntry

                while (pointer != null && pointer != start) {
                    if (pointer !is LinkedEntry.Boundary) counter++
                    pointer = pointer.successor
                }

                return counter
            }
        private val end: LinkedEntry.Boundary?
            get() = (last ?: start).successor as LinkedEntry.Boundary?

        private val first: LinkedEntry.Value? get() = start.successor.takeIf { it is LinkedEntry.Value } as LinkedEntry.Value?
        private val last: LinkedEntry.Value?
            get() {
                var pointer: LinkedEntry.Value? = null
                overAllEntries { pointer = it }
                return pointer
            }

        val size: Int
            get() {
                var counter = 0
                overAllEntries { counter++ }
                return counter
            }

        // ------------------------------------------- //

        fun clear() {
            if (size > 0) synchronized(backedList.lock) {
                val positionStart = startIndex
                val itemCount = size

                end.let { end ->
                    if (end == null) backedList.lastEntry = start
                    else end.predecessor = start

                    start.successor = end
                }

                notifyItemRangeRemoved(positionStart, itemCount)
            }
        }

        fun add(value: DayEntry) {
            synchronized(backedList.lock) {
                val position = startIndex + size
                val predecessor = last ?: start
                val successor = end

                LinkedEntry.Value(value, predecessor, successor).also { predecessor.successor = it }
                    .also {
                        if (successor != null) successor.predecessor =
                            it else backedList.lastEntry = it
                    }

                notifyItemInserted(position)
            }
        }

        fun addAll(vararg values: DayEntry) = addAll(listOf(*values))
        fun addAll(values: Collection<DayEntry>) {
            if (values.isNotEmpty()) synchronized(backedList.lock) {
                val positionStart = startIndex + size
                val itemCount = values.size


                insertAll(last ?: start, end, values)
                notifyItemRangeInserted(positionStart, itemCount)
            }
        }

        fun add(index: Int, value: DayEntry) {
            synchronized(backedList.lock) {
                val position = startIndex + index
                val (predecessor, successor) = getBorders(index)
                LinkedEntry.Value(value, predecessor, successor).also { predecessor.successor = it }
                    .also {
                        if (successor != null) successor.predecessor =
                            it else backedList.lastEntry = it
                    }
                notifyItemInserted(position)
            }
        }

        fun addAll(index: Int, vararg values: DayEntry) {
            addAll(index, listOf(*values))
        }

        fun addAll(index: Int, values: Collection<DayEntry>) {
            if (values.isNotEmpty()) synchronized(backedList.lock) {
                val positionStart = startIndex + index
                val itemCount = values.size

                val (predecessor, successor) = getBorders(index)
                insertAll(predecessor, successor, values)

                notifyItemRangeInserted(positionStart, itemCount)
            }
        }

        operator fun set(index: Int, value: DayEntry): DayEntry {
            synchronized(backedList.lock) {
                val entry = getEntry(index)

                val oldValue = entry.value
                entry.value = value
                notifyItemChanged(index)
                return oldValue
            }
        }

        operator fun get(index: Int): DayEntry {
            synchronized(backedList.lock) {
                return getEntry(index).value
            }
        }

        // ------------------------------------------- //

        var uiThread: Executor = Executor { it.run() }
        private fun notify(
            block: SingleDayEntryAdapter.() -> Unit
        ) = uiThread.execute { backedList.block() }

        private fun notifyItemRangeRemoved(
            positionStart: Int, itemCount: Int
        ) = notify { notifyItemRangeRemoved(positionStart, itemCount) }

        private fun notifyItemInserted(position: Int) = notify { notifyItemInserted(position) }
        private fun notifyItemRangeInserted(
            positionStart: Int, itemCount: Int
        ) = notify { notifyItemRangeInserted(positionStart, itemCount) }

        private fun notifyItemChanged(index: Int) = notify { notifyItemChanged(index) }

        // ------------------------------------------- //

        private fun insertAll(
            predecessor: LinkedEntry, successor: LinkedEntry?, values: Collection<DayEntry>
        ) {
            var pointer = predecessor

            values.forEach {
                val next = LinkedEntry.Value(it, pointer, null)
                pointer.successor = next
                pointer = next
            }

            pointer.successor.also { pointer.successor = successor }.also {
                if (successor != null) successor.predecessor = it else backedList.lastEntry = it
            }
        }

        private fun getBorders(index: Int): Pair<LinkedEntry, LinkedEntry?> {
            var predecessor: LinkedEntry = start
            var counter = 0
            overAllEntriesWhile {
                if (++counter == index) predecessor = it
                counter < index
            }

            return predecessor to predecessor.successor
        }

        private fun getEntry(index: Int): LinkedEntry.Value {
            if (index in 0 until size) {
                var count = 0
                var pointer = first!!
                overAllEntriesWhile {
                    pointer = pointer.successor as LinkedEntry.Value
                    count++
                    count < index
                }
                return pointer
            } else throw IndexOutOfBoundsException()
        }

        private fun overAllEntries(
            block: (LinkedEntry.Value) -> Unit
        ) = overAllEntriesWhile { block(it).let { true } }

        private fun overAllEntriesWhile(block: (LinkedEntry.Value) -> Boolean) {
            var pointer = start.successor
            while (pointer != null && pointer !is LinkedEntry.Boundary) {
                if (pointer is LinkedEntry.Value && !block(pointer)) break
                pointer = pointer.successor
            }
        }
    }

}

class DayEntryViewHolder(
    val binding: MaterialCardViewBinding
) : RecyclerView.ViewHolder(binding.root)

open class DayEntry(
    val title: String? = null, val subtitle: String? = null, val text: String? = null,
    val icon: Drawable? = null, val onClick: View.OnClickListener? = null,
    val buttons: Map<String, View.OnClickListener?>? = null
)