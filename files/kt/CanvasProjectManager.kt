package com.bitoneko.kouecanvas

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.File
import java.io.FileOutputStream

data class CanvasPreset(val name: String, val width: Int, val height: Int)

class CanvasProjectAdapter(
    private val ctx: Context,
    private var dataset: ArrayList<ProjectItem>
) : BaseAdapter() {

    private val thumbnailCache = android.util.LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    )

    override fun getCount(): Int = dataset.size
    override fun getItem(position: Int): ProjectItem = dataset[position]
    override fun getItemId(position: Int): Long = position.toLong()

    fun updateData(newDataset: ArrayList<ProjectItem>) {
        this.dataset = newDataset
        thumbnailCache.evictAll()
        notifyDataSetChanged()
        
        if (ctx is Activity) {
            val emptyContainerId = ctx.resources.getIdentifier("empty_container", "id", ctx.packageName)
            if (emptyContainerId != 0) {
                val emptyView = ctx.findViewById<View>(emptyContainerId)
                emptyView?.visibility = if (dataset.size > 0) View.GONE else View.VISIBLE
            }
        }
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(ctx).inflate(
            ctx.resources.getIdentifier("project_list_element", "layout", ctx.packageName).takeIf { it != 0 }
                ?: android.R.layout.simple_list_item_2, parent, false
        )
        val item = getItem(position)
        val pkg = ctx.packageName
        
        val txtNameId = ctx.resources.getIdentifier("txt_name", "id", pkg)
        val imgPreviewId = ctx.resources.getIdentifier("img_preview", "id", pkg)
        
        if (txtNameId != 0) {
            view.findViewById<TextView>(txtNameId)?.text = item.name
        }
        if (imgPreviewId != 0) {
            val imgView = view.findViewById<ImageView>(imgPreviewId)
            if (imgView != null) {
                imgView.setImageBitmap(null)
                val cachedBmp = thumbnailCache.get(item.id)
                if (cachedBmp != null) {
                    imgView.setImageBitmap(cachedBmp)
                } else {
                    val f = File(item.previewPath)
                    if (f.exists()) {
                        val density = ctx.resources.displayMetrics.density
                        val targetSize = (96 * density).toInt()
                        val bmp = decodeSampledBitmapFromFile(f.absolutePath, targetSize, targetSize)
                        if (bmp != null) {
                            thumbnailCache.put(item.id, bmp)
                            imgView.setImageBitmap(bmp)
                        } else {
                            imgView.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    } else {
                        imgView.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            }
        }
        return view
    }
}

object CanvasProjectManager {

    private fun getSortedProjects(context: Context): ArrayList<ProjectItem> {
        val items = ProjectStorageManager.getAllProjects(context)
        val prefs = context.getSharedPreferences("canvas_settings", Context.MODE_PRIVATE)
        val sortRecentTop = prefs.getBoolean("sort_recent_top", false)
        
        if (sortRecentTop) {
            items.sortByDescending { it.lastModified }
        }
        return items
    }
    
    fun generateUniqueName(context: Context, baseName: String): String {
        val list = ProjectStorageManager.getAllProjects(context)
        var uniqueName = baseName
        var index = 1
        while (list.any { it.name.equals(uniqueName, ignoreCase = true) }) {
            uniqueName = "$baseName ($index)"
            index++
        }
        return uniqueName
    }

    fun setupProjectsList(act: Activity, listView: ListView, fab: ExtendedFloatingActionButton) {
        val context = act.applicationContext
        val items = getSortedProjects(context)
        val adapter = CanvasProjectAdapter(act, items)
        listView.adapter = adapter
        
        val emptyContainerId = act.resources.getIdentifier("empty_container", "id", act.packageName)
        if (emptyContainerId != 0) {
            val emptyView = act.findViewById<View>(emptyContainerId)
            emptyView?.visibility = if (items.size > 0) View.GONE else View.VISIBLE
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val currentItems = getSortedProjects(context)
            if (position >= 0 && position < currentItems.size) {
                val p = currentItems[position]
                val i = Intent(act, CanvasActivity::class.java).apply {
                    putExtra("PROJECT_ID", p.id)
                    putExtra("PROJECT_NAME", p.name)
                }
                act.startActivity(i)
            }
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val currentItems = getSortedProjects(context)
            if (position >= 0 && position < currentItems.size) {
                val p = currentItems[position]
                MaterialAlertDialogBuilder(act)
                    .setTitle("Delete Project")
                    .setMessage("Are you sure you want to permanently delete '${p.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        ProjectStorageManager.deleteProject(context, p.id)
                        val updatedItems = getSortedProjects(context)
                        adapter.updateData(updatedItems)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            true
        }
        listView.setOnScrollListener(object : AbsListView.OnScrollListener {
            private var lastFirstVisibleItem = 0
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                val rootVg = act.window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
                
                if (firstVisibleItem > lastFirstVisibleItem) {
                    if (fab.isExtended) {
                        fab.shrink()
                        val travelDistance = (rootVg.width / 2f) - (fab.width / 4f)
                        fab.animate()
                            .translationX(travelDistance)
                            .setDuration(200)
                            .start()
                    }
                } else if (firstVisibleItem < lastFirstVisibleItem) {
                    if (!fab.isExtended) {
                        fab.extend()
                        fab.animate()
                            .translationX(0f)
                            .setDuration(200)
                            .start()
                    }
                }
                lastFirstVisibleItem = firstVisibleItem
            }
        })
    }
    
    fun openNewProjectDialog(act: Activity, fab: ExtendedFloatingActionButton?, listView: ListView?) {
        val presets = listOf(
            CanvasPreset("Square", 1000, 1000),
            CanvasPreset("Full HD", 1920, 1080),
            CanvasPreset("HD Vertical", 1080, 1920),
            CanvasPreset("4K Ultra", 3840, 2160),
            CanvasPreset("Avatar", 512, 512),
            CanvasPreset("Banner", 1200, 630)
        )

        var selectedPresetIndex = 0
        var inputNameRef: com.google.android.material.textfield.TextInputEditText? = null

        val density = act.resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        val rootLayout = android.widget.LinearLayout(act).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(8))
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val inputLayout = com.google.android.material.textfield.TextInputLayout(act).apply {
            hint = "Project Name"
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val inputName = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            isSingleLine = true
            setText("New SM Project")
            selectAll()
        }
        inputNameRef = inputName
        inputLayout.addView(inputName)
        rootLayout.addView(inputLayout)

        rootLayout.addView(TextView(act).apply {
            text = "Canvas Size"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
                bottomMargin = dp(8)
            }
        })

        val gridView = android.widget.GridView(act).apply {
            numColumns = 2
            horizontalSpacing = dp(12)
            verticalSpacing = dp(12)
            stretchMode = android.widget.GridView.STRETCH_COLUMN_WIDTH
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rootLayout.addView(gridView)

        val itemHeight = dp(110)

        val presetAdapter = object : BaseAdapter() {
            override fun getCount(): Int = presets.size
            override fun getItem(position: Int): CanvasPreset = presets[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
                val preset = getItem(pos)
                val typedValue = android.util.TypedValue()
                
                act.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerLow, typedValue, true)
                val colorUnselected = typedValue.data
                act.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
                val colorSelected = typedValue.data

                val card = com.google.android.material.card.MaterialCardView(act).apply {
                    radius = dp(12).toFloat()
                    strokeWidth = if (pos == selectedPresetIndex) dp(2) else 0
                    setCardBackgroundColor(android.content.res.ColorStateList.valueOf(if (pos == selectedPresetIndex) colorSelected else colorUnselected))
                    
                    val outValue = android.util.TypedValue()
                    act.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    foreground = act.getDrawable(outValue.resourceId)
                    
                    layoutParams = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight)
                }

                val cardContent = android.widget.LinearLayout(act).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                }

                val previewContainer = android.widget.FrameLayout(act)
                previewContainer.setBackgroundColor(android.graphics.Color.LTGRAY)
                val containerParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (itemHeight * 0.4f).toInt())
                containerParams.bottomMargin = dp(6)
                previewContainer.layoutParams = containerParams

                val canvasPreviewFrame = View(act)
                canvasPreviewFrame.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    setStroke(dp(1), android.graphics.Color.DKGRAY)
                }

                val maxW = dp(110)
                val maxH = (itemHeight * 0.4f).toInt()
                val ratio = preset.width.toFloat() / preset.height.toFloat()
                val newW: Int
                val newH: Int

                if (ratio > 1) {
                    newW = maxW
                    newH = (maxW / ratio).toInt().coerceAtMost(maxH)
                } else {
                    newH = maxH
                    newW = (maxH * ratio).toInt().coerceAtMost(maxW)
                }

                canvasPreviewFrame.layoutParams = android.widget.FrameLayout.LayoutParams(newW, newH, android.view.Gravity.CENTER)
                
                previewContainer.addView(canvasPreviewFrame)
                cardContent.addView(previewContainer)

                cardContent.addView(TextView(act).apply {
                    text = preset.name
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                })

                cardContent.addView(TextView(act).apply {
                    text = "${preset.width} × ${preset.height}"
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                })

                card.addView(cardContent)
                return card
            }
        }

        gridView.adapter = presetAdapter
        gridView.setOnItemClickListener { _, _, position, _ ->
            selectedPresetIndex = position
            presetAdapter.notifyDataSetChanged()
        }

        val dialog = MaterialAlertDialogBuilder(act)
            .setTitle("Create New Project")
            .setView(rootLayout)
            .setPositiveButton("Create") { _, _ ->
                val enteredName = inputNameRef?.text.toString().trim().ifEmpty { "New SM Project" }
                val uniqueName = generateUniqueName(act, enteredName)
                val projId = System.currentTimeMillis().toString()
                
                val selectedPreset = presets[selectedPresetIndex]
                val width = selectedPreset.width
                val height = selectedPreset.height

                val emptyBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                autoSaveCanvasWorkspace(act, projId, uniqueName, width, height, emptyBmp)
                emptyBmp.recycle() 
                
                listView?.let { refreshListView(act, it) }
                
                val intent = Intent(act, CanvasActivity::class.java).apply {
                    putExtra("PROJECT_NAME", uniqueName)
                    putExtra("PROJECT_ID", projId)
                }
                act.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun refreshListView(act: Activity, listView: ListView) {
        val items = getSortedProjects(act.applicationContext)
        
        val emptyContainerId = act.resources.getIdentifier("empty_container", "id", act.packageName)
        if (emptyContainerId != 0) {
            val emptyView = act.findViewById<View>(emptyContainerId)
            emptyView?.visibility = if (items.size > 0) View.GONE else View.VISIBLE
        }

        val adapter = listView.adapter
        if (adapter is CanvasProjectAdapter) {
            adapter.updateData(items)
        } else {
            listView.adapter = CanvasProjectAdapter(act, items)
        }
    }

    fun autoSaveCanvasWorkspace(context: Context, id: String, name: String, width: Int, height: Int, canvasBitmap: Bitmap) {
        try {
            val pFile = File(context.filesDir, "preview_$id.png")
            val out = FileOutputStream(pFile)
            canvasBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.flush()
            out.close()

            val item = ProjectItem(
                id = id,
                name = name,
                width = width,
                height = height,
                lastModified = System.currentTimeMillis(),
                previewPath = pFile.absolutePath
            )
            ProjectStorageManager.saveProjectMetadata(context, item)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
