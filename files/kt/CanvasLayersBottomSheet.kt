package com.bitoneko.kouecanvas

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CanvasLayersBottomSheet(
    private val cv: InteractiveCanvasView
) : DialogFragment() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: LayersAdapter
    private var touchHelper: ItemTouchHelper? = null

    private val panelWidthDp = 360

    private var selectedLayerIndex: Int = cv.activeLayerIndex

    private inner class LayerViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val row: LinearLayout = view.findViewById(101)
        val preview: ImageView = view.findViewById(102)
        val name: TextView = view.findViewById(103)
        val visibility: ImageView = view.findViewById(104)
        val delete: ImageView = view.findViewById(105)
    }

    private inner class LayersAdapter :
        RecyclerView.Adapter<LayerViewHolder>() {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): LayerViewHolder {
            val d = parent.resources.displayMetrics.density

            val row = LinearLayout(parent.context).apply {
                id = 101
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (104 * d).toInt()
                )
                setPadding(
                    (8 * d).toInt(),
                    (8 * d).toInt(),
                    (6 * d).toInt(),
                    (8 * d).toInt()
                )
            }

            val preview = ImageView(parent.context).apply {
                id = 102
                layoutParams = LinearLayout.LayoutParams(
                    (82 * d).toInt(),
                    (82 * d).toInt()
                ).apply {
                    rightMargin = (14 * d).toInt()
                }
                setBackgroundColor(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(
                    (2 * d).toInt(),
                    (2 * d).toInt(),
                    (2 * d).toInt(),
                    (2 * d).toInt()
                )
            }

            val name = TextView(parent.context).apply {
                id = 103
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    rightMargin = (8 * d).toInt()
                }
                setTextColor(Color.WHITE)
                textSize = 15f
                maxLines = 2
                gravity = Gravity.CENTER_VERTICAL
            }

            val visibility = ImageView(parent.context).apply {
                id = 104
                layoutParams = LinearLayout.LayoutParams(
                    (44 * d).toInt(),
                    (44 * d).toInt()
                ).apply {
                    rightMargin = (6 * d).toInt()
                }
                setColorFilter(Color.WHITE)
            }

            val delete = ImageView(parent.context).apply {
                id = 105
                layoutParams = LinearLayout.LayoutParams(
                    (44 * d).toInt(),
                    (44 * d).toInt()
                )
                setImageResource(android.R.drawable.ic_menu_delete)
                setColorFilter(Color.RED)
            }

            row.addView(preview)
            row.addView(name)
            row.addView(visibility)
            row.addView(delete)

            return LayerViewHolder(row)
        }

        override fun getItemCount(): Int {
            return cv.layersList.size
        }

        private fun realIndex(position: Int): Int {
            return cv.layersList.lastIndex - position
        }

        override fun onBindViewHolder(
            holder: LayerViewHolder,
            position: Int
        ) {
            val real = realIndex(position)

            if (real !in cv.layersList.indices) {
                return
            }

            val layer = cv.layersList[real]
            val d = holder.itemView.resources.displayMetrics.density

            holder.row.setBackgroundColor(
                if (real == selectedLayerIndex) {
                    Color.parseColor("#33FFFFFF")
                } else {
                    Color.TRANSPARENT
                }
            )

            holder.name.text = layer.name

            holder.visibility.setImageResource(
                if (layer.isVisible) {
                    android.R.drawable.checkbox_on_background
                } else {
                    android.R.drawable.checkbox_off_background
                }
            )

            holder.delete.visibility =
                if (cv.layersList.size > 1) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            holder.preview.setImageBitmap(
                try {
                    Bitmap.createScaledBitmap(
                        layer.bitmap,
                        (82 * d).toInt(),
                        (82 * d).toInt(),
                        true
                    )
                } catch (_: Exception) {
                    null
                }
            )

            holder.row.setOnClickListener {
                val visual = holder.bindingAdapterPosition

                if (visual == RecyclerView.NO_POSITION) {
                    return@setOnClickListener
                }

                val index = realIndex(visual)

                if (index !in cv.layersList.indices) {
                    return@setOnClickListener
                }

                selectedLayerIndex = index
                cv.activeLayerIndex = index
                cv.invalidate()

                notifyDataSetChanged()
            }

            holder.visibility.setOnClickListener {
                val visual = holder.bindingAdapterPosition

                if (visual == RecyclerView.NO_POSITION) {
                    return@setOnClickListener
                }

                val index = realIndex(visual)

                if (index !in cv.layersList.indices) {
                    return@setOnClickListener
                }

                cv.setLayerVisibilityFromUI(
                    index,
                    !cv.layersList[index].isVisible
                )

                notifyItemChanged(visual)
            }

            holder.delete.setOnClickListener {
                val visual = holder.bindingAdapterPosition

                if (visual == RecyclerView.NO_POSITION) {
                    return@setOnClickListener
                }

                if (cv.layersList.size <= 1) {
                    return@setOnClickListener
                }

                val index = realIndex(visual)

                if (index !in cv.layersList.indices) {
                    return@setOnClickListener
                }

                cv.deleteLayerFromUI(index)

                when {
                    selectedLayerIndex == index -> {
                        selectedLayerIndex =
                            selectedLayerIndex.coerceIn(
                                0,
                                cv.layersList.lastIndex
                            )
                    }

                    index < selectedLayerIndex -> {
                        selectedLayerIndex--
                    }
                }

                cv.activeLayerIndex = selectedLayerIndex.coerceIn(
                    0,
                    cv.layersList.lastIndex
                )

                notifyDataSetChanged()
            }
        }
    }

    override fun onCreateDialog(
        savedInstanceState: Bundle?
    ): Dialog {
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(createContent())
            setCanceledOnTouchOutside(true)

            setOnShowListener {
                window?.let(::configureWindow)
            }
        }
    }

    private fun configureWindow(window: Window) {
        val d = resources.displayMetrics.density

        window.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        window.setGravity(Gravity.END)

        window.attributes = window.attributes.apply {
            width = (panelWidthDp * d).toInt()
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.END
            dimAmount = 0.32f
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )
    }

    private fun createContent(): View {
        val d = resources.displayMetrics.density

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (18 * d).toInt(),
                (22 * d).toInt(),
                (12 * d).toInt(),
                (18 * d).toInt()
            )

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF2D2A30"))
                cornerRadii = floatArrayOf(
                    22f * d,
                    22f * d,
                    0f,
                    0f,
                    0f,
                    0f,
                    22f * d,
                    22f * d
                )
            }
        }

        val title = TextView(requireContext()).apply {
            text = "Layers"
            setTextColor(Color.WHITE)
            textSize = 21f
            setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
            layoutParams = LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                bottomMargin = (12 * d).toInt()
            }
        }

        root.addView(title)

        rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            layoutParams = LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
            clipToPadding = false
            setPadding(
                0,
                0,
                0,
                (6 * d).toInt()
            )
        }

        adapter = LayersAdapter()
        rv.adapter = adapter
        root.addView(rv)

        val addButton = Button(requireContext()).apply {
            text = "Create New Layer"
            layoutParams = LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin = (12 * d).toInt()
            }

            setOnClickListener {
                cv.addLayerFromUI()

                selectedLayerIndex =
                    cv.activeLayerIndex.coerceIn(
                        0,
                        cv.layersList.lastIndex
                    )

                adapter.notifyDataSetChanged()
                rv.scrollToPosition(0)
            }
        }

        root.addView(addButton)

        touchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromVisual =
                        viewHolder.bindingAdapterPosition

                    val toVisual =
                        target.bindingAdapterPosition

                    if (
                        fromVisual == RecyclerView.NO_POSITION ||
                        toVisual == RecyclerView.NO_POSITION
                    ) {
                        return false
                    }

                    val fromReal =
                        cv.layersList.lastIndex - fromVisual

                    val toReal =
                        cv.layersList.lastIndex - toVisual

                    if (
                        fromReal !in cv.layersList.indices ||
                        toReal !in cv.layersList.indices
                    ) {
                        return false
                    }

                    val moved =
                        cv.layersList.removeAt(fromReal)

                    cv.layersList.add(
                        toReal,
                        moved
                    )

                    selectedLayerIndex = when {
                        selectedLayerIndex == fromReal -> {
                            toReal
                        }

                        fromReal < toReal &&
                            selectedLayerIndex in
                            (fromReal + 1)..toReal -> {
                            selectedLayerIndex - 1
                        }

                        toReal < fromReal &&
                            selectedLayerIndex in
                            toReal until fromReal -> {
                            selectedLayerIndex + 1
                        }

                        else -> {
                            selectedLayerIndex
                        }
                    }

                    cv.activeLayerIndex = selectedLayerIndex

                    adapter.notifyItemMoved(
                        fromVisual,
                        toVisual
                    )

                    cv.invalidate()

                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int
                ) = Unit

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(
                        recyclerView,
                        viewHolder
                    )

                    adapter.notifyDataSetChanged()
                }
            }
        )

        touchHelper?.attachToRecyclerView(rv)

        return root
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.let(::configureWindow)
    }

    override fun onDestroyView() {
        touchHelper?.attachToRecyclerView(null)
        touchHelper = null
        super.onDestroyView()
    }
}