package com.bitoneko.kouecanvas

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

object CanvasToolsDialogManager {

    private val recentColorsList = ArrayList<Int>()

    fun showToolsSelectorDialog(act: Activity, cv: InteractiveCanvasView, imgSelectedTool: ImageView) {
        val density = act.resources.displayMetrics.density
        val scroll = ScrollView(act)
        val root = LinearLayout(act).apply { 
            orientation = LinearLayout.VERTICAL 
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()) 
        }
        scroll.addView(root)

        val basicItems = arrayOf<Triple<String, String, ToolMode>>(
            Triple("Brush", "ic_brush", ToolMode.BRUSH),
            Triple("Eraser", "ic_eraser", ToolMode.ERASER),
            Triple("Pan", "ic_hand", ToolMode.PAN),
            Triple("Fill", "ic_fill", ToolMode.FILL),
            Triple("Image Stamp", "ic_image", ToolMode.STAMP_IMAGE),
            Triple("Text Stamp", "ic_text", ToolMode.STAMP_TEXT),
            Triple("Color Picker", "ic_pipette", ToolMode.PIPPETE)
        )

        val extraItems = arrayOf<Triple<String, String, ToolMode>>(
            Triple("Line", "ic_line", ToolMode.LINE),
            Triple("Rectangle", "ic_rectangle", ToolMode.RECTANGLE),
            Triple("Circle / Oval", "ic_circle", ToolMode.CIRCLE),
            Triple("Arrow", "ic_arrow", ToolMode.ARROW),
            Triple("Lasso Select", "ic_lasso", ToolMode.LASSO),
            Triple("Rect Select", "ic_select_rect", ToolMode.SELECT_RECT),
            Triple("Airbrush", "ic_spray", ToolMode.AIRBRUSH),
            Triple("Blur", "ic_blur", ToolMode.BLUR),
            Triple("Smudge", "ic_smudge", ToolMode.SMUDGE),
            Triple("Gradient", "ic_gradient", ToolMode.GRADIENT),
            Triple("Transform Layer", "ic_transform", ToolMode.TRANSFORM),
            Triple("Vector Pen", "ic_pen", ToolMode.PEN_VECTOR),
            Triple("Crop Canvas", "ic_crop", ToolMode.CROP),
            Triple("Symmetry", "ic_symmetry", ToolMode.SYMMETRY),
            Triple("Color Adjust", "ic_adjust", ToolMode.COLOR_ADJUST)
        )

        val dialog = MaterialAlertDialogBuilder(act).setTitle("Tools").setView(scroll).create()

        fun addToolRow(item: Triple<String, String, ToolMode>) {
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                val v = android.util.TypedValue()
                act.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
                setBackgroundResource(v.resourceId)
                isClickable = true
            }
            val iv = ImageView(act).apply { 
                layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply { 
                    rightMargin = (16 * density).toInt() 
                }
                val iconId = act.resources.getIdentifier(item.second, "drawable", act.packageName)
                setImageResource(if (iconId != 0) iconId else android.R.drawable.ic_menu_info_details)
                setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN) 
            }
            val tv = TextView(act).apply { 
                text = item.first
                setTextColor(Color.WHITE)
                textSize = 16f 
            }
            row.addView(iv)
            row.addView(tv)
            row.setOnClickListener {
                cv.currentTool = item.third
                val activeIconId = act.resources.getIdentifier(item.second, "drawable", act.packageName)
                if (activeIconId != 0) {
                    imgSelectedTool.setImageResource(activeIconId)
                    imgSelectedTool.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
                }
                dialog.dismiss()
            }
            root.addView(row)
        }

        for (item in basicItems) {
            addToolRow(item)
        }

        val moreRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt())
            val v = android.util.TypedValue()
            act.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
            setBackgroundResource(v.resourceId)
            isClickable = true
        }
        val tvMore = TextView(act).apply {
            text = "More..."
            setTextColor(Color.parseColor("#888888"))
            textSize = 15f
        }
        moreRow.addView(tvMore)

        moreRow.setOnClickListener {
            root.removeView(moreRow)
            for (item in extraItems) {
                addToolRow(item)
            }
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }

        root.addView(moreRow)
        dialog.show()
    }

    fun showActiveToolSettingsDialog(act: Activity, cv: InteractiveCanvasView) {
        val density = act.resources.displayMetrics.density
        val scroll = ScrollView(act)

        fun configTitle(name: String) = "$name Configuration"

        when (cv.currentTool) {
            ToolMode.BRUSH -> showSliderDialog(act, "Brush Size", cv.brushSize.toInt(), 1, 200, configTitle("Brush")) { cv.brushSize = it.toFloat() }
            ToolMode.ERASER -> showSliderDialog(act, "Eraser Size", cv.eraserSize.toInt(), 1, 200, configTitle("Eraser")) { cv.eraserSize = it.toFloat() }
            ToolMode.FILL -> showSliderDialog(act, "Color Tolerance", cv.fillTolerance, 0, 150, configTitle("Fill")) { cv.fillTolerance = it }

            ToolMode.LINE -> showSliderDialog(act, "Line Thickness", cv.brushSize.toInt(), 1, 200, configTitle("Line")) { cv.brushSize = it.toFloat() }
            ToolMode.RECTANGLE -> showSliderDialog(act, "Border Thickness", cv.brushSize.toInt(), 1, 200, configTitle("Rectangle")) { cv.brushSize = it.toFloat() }
            ToolMode.CIRCLE -> showSliderDialog(act, "Border Thickness", cv.brushSize.toInt(), 1, 200, configTitle("Circle / Oval")) { cv.brushSize = it.toFloat() }
            ToolMode.ARROW -> showSliderDialog(act, "Arrow Thickness", cv.brushSize.toInt(), 1, 200, configTitle("Arrow")) { cv.brushSize = it.toFloat() }
            ToolMode.AIRBRUSH -> showSliderDialog(act, "Spray Radius", cv.brushSize.toInt(), 1, 200, configTitle("Airbrush")) { cv.brushSize = it.toFloat() }
            ToolMode.BLUR -> showSliderDialog(act, "Blur Radius", cv.brushSize.toInt(), 1, 200, configTitle("Blur")) { cv.brushSize = it.toFloat() }
            
            ToolMode.SMUDGE -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)

                val tvSize = TextView(act).apply { text = "Smudge Size: ${cv.brushSize.toInt()}"; setTextColor(Color.WHITE) }
                val sbSize = SeekBar(act).apply { max = 199; progress = cv.brushSize.toInt() - 1; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cv.brushSize = (p + 1).toFloat(); tvSize.text = "Smudge Size: ${p + 1}" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}
                
                val tvStr = TextView(act).apply { text = "Strength: ${(cv.smudgeStrength * 100).toInt()}%"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbStr = SeekBar(act).apply { max = 100; progress = (cv.smudgeStrength * 100).toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cv.smudgeStrength = p / 100f; tvStr.text = "Strength: $p%" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}

                layout.addView(tvSize); addSeekBarWithNumberInput(act,layout,sbSize,1,200){cv.brushSize=it.toFloat();tvSize.text="Smudge Size: $it"};layout.addView(tvStr);addSeekBarWithNumberInput(act,layout,sbStr,0,100){cv.smudgeStrength=it/100f;tvStr.text="Strength: $it%"}
                MaterialAlertDialogBuilder(act).setTitle(configTitle("Smudge")).setView(scroll).setPositiveButton("OK", null).show()
            }

            ToolMode.GRADIENT -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)

                val rgType = RadioGroup(act).apply { orientation = RadioGroup.HORIZONTAL }
                val rbLinear = RadioButton(act).apply { text = "Linear"; setTextColor(Color.WHITE); id = View.generateViewId() }
                val rbRadial = RadioButton(act).apply { text = "Radial"; setTextColor(Color.WHITE); id = View.generateViewId() }
                rgType.addView(rbLinear); rgType.addView(rbRadial)

                if (cv.gradientType == GradientType.LINEAR) rgType.check(rbLinear.id) else rgType.check(rbRadial.id)
                rgType.setOnCheckedChangeListener { _, checkedId ->
                    cv.gradientType = if (checkedId == rbLinear.id) GradientType.LINEAR else GradientType.RADIAL
                }

                val btnEndColor = Button(act).apply { text = "Select Secondary Color"; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                btnEndColor.setOnClickListener {
                    showColorPickerDialog(act, cv, ImageView(act))
                }

                layout.addView(rgType)
                layout.addView(btnEndColor)
                MaterialAlertDialogBuilder(act).setTitle(configTitle("Gradient")).setView(scroll).setPositiveButton("OK", null).show()
            }

            ToolMode.TRANSFORM -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)

                val btnApply = Button(act).apply { text = "Apply Transformation" }
                btnApply.setOnClickListener {
                    cv.applyTransformToActiveLayer()
                    Toast.makeText(act, "Transformation applied to active layer", Toast.LENGTH_SHORT).show()
                }
                layout.addView(btnApply)
                MaterialAlertDialogBuilder(act).setTitle(configTitle("Transform Layer")).setView(scroll).setPositiveButton("Close", null).show()
            }

            ToolMode.PEN_VECTOR -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)

                val btnBake = Button(act).apply { text = "Apply Path to Layer" }
                btnBake.setOnClickListener {
                    cv.bakeVectorPathToLayer(false)
                    Toast.makeText(act, "Vector stroke applied", Toast.LENGTH_SHORT).show()
                }

                val btnBakeClosed = Button(act).apply { text = "Close & Apply Shape" }
                btnBakeClosed.setOnClickListener {
                    cv.bakeVectorPathToLayer(true)
                    Toast.makeText(act, "Closed vector and shape applied", Toast.LENGTH_SHORT).show()
                }

                val btnClear = Button(act).apply { text = "Clear Nodes" }
                btnClear.setOnClickListener {
                    cv.vectorPoints.clear()
                    cv.vectorPath.reset()
                    cv.invalidate()
                }

                layout.addView(btnBake); layout.addView(btnBakeClosed); layout.addView(btnClear)
                MaterialAlertDialogBuilder(act).setTitle(configTitle("Vector Pen")).setView(scroll).setPositiveButton("Close", null).show()
            }

            ToolMode.CROP -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)

                val btnCrop = Button(act).apply { text = "Crop Canvas to Frame" }
                btnCrop.setOnClickListener {
                    cv.applyCropCanvas()
                    Toast.makeText(act, "Canvas cropped", Toast.LENGTH_SHORT).show()
                }
                layout.addView(btnCrop)
                MaterialAlertDialogBuilder(act).setTitle(configTitle("Crop Canvas")).setView(scroll).setPositiveButton("Close", null).show()
            }

            ToolMode.SYMMETRY -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)

                val modes = arrayOf("Disabled", "Horizontal", "Vertical", "Radial")
                val spinner = Spinner(act).apply {
                    adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, modes)
                    setSelection(cv.symmetryMode.ordinal)
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                            cv.symmetryMode = SymmetryMode.values()[pos]
                            cv.invalidate()
                        }
                        override fun onNothingSelected(p0: AdapterView<*>?) {}
                    }
                }
                layout.addView(spinner)

                val tvSectors = TextView(act).apply { text = "Radial Sectors: ${cv.symmetryRadialSectors}"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbSectors = SeekBar(act).apply { max = 14; progress = cv.symmetryRadialSectors - 2; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cv.symmetryRadialSectors = p + 2; tvSectors.text = "Radial Sectors: ${p + 2}"; cv.invalidate() }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}

                layout.addView(tvSectors);addSeekBarWithNumberInput(act,layout,sbSectors,2,16){cv.symmetryRadialSectors=it;tvSectors.text="Radial Sectors: $it";cv.invalidate()}
                MaterialAlertDialogBuilder(act).setTitle(configTitle("Symmetry")).setView(scroll).setPositiveButton("OK", null).show()
            }

            ToolMode.COLOR_ADJUST -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)

                var bVal = 0f
                var cVal = 1f
                var sVal = 1f

                val tvB = TextView(act).apply { text = "Brightness: 0"; setTextColor(Color.WHITE) }
                val sbB = SeekBar(act).apply { max = 200; progress = 100; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { bVal = (p - 100) / 100f; tvB.text = "Brightness: ${(bVal * 100).toInt()}" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}

                val tvC = TextView(act).apply { text = "Contrast: 1.0"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbC = SeekBar(act).apply { max = 200; progress = 100; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cVal = p / 100f; tvC.text = "Contrast: $cVal" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}

                val tvS = TextView(act).apply { text = "Saturation: 1.0"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbS = SeekBar(act).apply { max = 200; progress = 100; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { sVal = p / 100f; tvS.text = "Saturation: $sVal" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}

                val btnApply = Button(act).apply { text = "Apply Adjustments"; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (16 * density).toInt() } }
                btnApply.setOnClickListener {
                    cv.applyColorAdjustments(bVal, cVal, sVal)
                    Toast.makeText(act, "Adjustments applied", Toast.LENGTH_SHORT).show()
                }

                val btnBlur = Button(act).apply { text = "Apply Gaussian Blur"; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * density).toInt() } }
                btnBlur.setOnClickListener {
                    cv.applyGaussianBlurToActiveLayer(10f)
                    Toast.makeText(act, "Gaussian blur applied", Toast.LENGTH_SHORT).show()
                }

                layout.addView(tvB); layout.addView(sbB)
                layout.addView(tvC); layout.addView(sbC)
                layout.addView(tvS); layout.addView(sbS)
                layout.addView(btnApply)
                layout.addView(btnBlur)

                MaterialAlertDialogBuilder(act).setTitle(configTitle("Color Adjust")).setView(scroll).setPositiveButton("Close", null).show()
            }

            ToolMode.LASSO, ToolMode.SELECT_RECT -> {
                val toolName = if (cv.currentTool == ToolMode.LASSO) "Lasso Select" else "Rect Select"
                
                val layout = LinearLayout(act).apply { 
                    orientation = LinearLayout.VERTICAL
                    setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (16 * density).toInt()) 
                }
                scroll.addView(layout)

                fun invokeCanvasMethod(methodName: String, successMessage: String) {
                    try {
                        val method = cv.javaClass.getMethod(methodName)
                        method.invoke(cv)
                        Toast.makeText(act, successMessage, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        cv.postInvalidate()
                        Toast.makeText(act, "$successMessage (Updated)", Toast.LENGTH_SHORT).show()
                    }
                }

                val actions = listOf(
                    "Copy" to Runnable { invokeCanvasMethod("copySelection", "Copied to clipboard") },
                    "Cut" to Runnable { invokeCanvasMethod("cutSelection", "Cut to clipboard") },
                    "Paste" to Runnable { invokeCanvasMethod("pasteSelection", "Pasted from clipboard") },
                    "Delete Selected Area" to Runnable { invokeCanvasMethod("deleteSelectionContent", "Area deleted") },
                    "Deselect" to Runnable { invokeCanvasMethod("clearSelection", "Selection cleared") },
                    "Invert Selection" to Runnable { invokeCanvasMethod("invertSelection", "Selection inverted") },
                    "Fill Selection" to Runnable { invokeCanvasMethod("fillSelection", "Selection filled") }
                )

                for ((label, action) in actions) {
                    val btn = Button(act).apply {
                        text = label
                        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = (8 * density).toInt()
                        }
                        setOnClickListener {
                            action.run()
                        }
                    }
                    layout.addView(btn)
                }

                MaterialAlertDialogBuilder(act)
                    .setTitle(configTitle(toolName))
                    .setView(scroll)
                    .setPositiveButton("Close", null)
                    .show()
            }

            ToolMode.PAN -> {
                MaterialAlertDialogBuilder(act)
                    .setTitle(configTitle("Pan"))
                    .setMessage("Drag with one finger to move around the canvas, or pinch with two fingers to zoom in and out.")
                    .setPositiveButton("OK", null)
                    .show()
            }

            ToolMode.PIPPETE -> {
                MaterialAlertDialogBuilder(act)
                    .setTitle(configTitle("Color Picker"))
                    .setMessage("Tap on any pixel on the canvas to pick its color.")
                    .setPositiveButton("OK", null)
                    .show()
            }

            ToolMode.STAMP_TEXT -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)

                val et = EditText(act).apply { 
                    hint = "Text For Stamp"
                    setText(cv.stampText)
                    isFocusableInTouchMode = true
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setSingleLine(false)
                    setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION)
                    requestFocus()
                }
                layout.addView(et)

                val tvSize = TextView(act).apply { text = "Text Size: ${cv.stampTextSize.toInt()}"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbSize = SeekBar(act).apply { max = 300; progress = cv.stampTextSize.toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cv.stampTextSize = p.toFloat(); tvSize.text = "Text Size: $p" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}
                val tvRot = TextView(act).apply { text = "Rotation Angle: ${cv.stampRotation.toInt()}°"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbRot = SeekBar(act).apply { max = 360; progress = cv.stampRotation.toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cv.stampRotation = p.toFloat(); tvRot.text = "Rotation Angle: $p°" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}
                layout.addView(tvSize);addSeekBarWithNumberInput(act,layout,sbSize,0,300){cv.stampTextSize=it.toFloat();tvSize.text="Text Size: $it"};layout.addView(tvRot);addSeekBarWithNumberInput(act,layout,sbRot,0,360){cv.stampRotation=it.toFloat();tvRot.text="Rotation Angle: ${it}°"}

                val textDialog = MaterialAlertDialogBuilder(act)
                    .setTitle(configTitle("Text Stamp"))
                    .setView(scroll)
                    .setPositiveButton("Save") { _, _ -> 
                        cv.stampText = et.text.toString()
                        val imm = act.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(et.windowToken, 0)
                    }
                    .setNeutralButton("Use Stamp") { d, _ ->
						cv.stampText = et.text.toString()
						cv.useStamp()

						val imm = act.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
						imm.hideSoftInputFromWindow(et.windowToken, 0)

						d.dismiss()
					}
                    .create()

                textDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                textDialog.show()
            }

            ToolMode.STAMP_IMAGE -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)
                val btnLoad = Button(act).apply { text = "Bind Photo From Gallery" }
                btnLoad.setOnClickListener { val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply { type = "image/*" }; act.startActivityForResult(intent, 999) }
                layout.addView(btnLoad)

                val tvScaleX = TextView(act).apply { text = "Width Scale: ${(cv.stampScaleX * 100).toInt()}%"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbScaleX = SeekBar(act).apply { max = 490; progress = ((cv.stampScaleX * 100).toInt() - 10); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { val sc = (p + 10) / 100f; cv.stampScaleX = sc; tvScaleX.text = "Width Scale: ${(sc * 100).toInt()}%" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}

                val tvScaleY = TextView(act).apply { text = "Height Scale: ${(cv.stampScaleY * 100).toInt()}%"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbScaleY = SeekBar(act).apply { max = 490; progress = ((cv.stampScaleY * 100).toInt() - 10); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { val sc = (p + 10) / 100f; cv.stampScaleY = sc; tvScaleY.text = "Height Scale: ${(sc * 100).toInt()}%" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}

                val tvRot = TextView(act).apply { text = "Rotation Angle: ${cv.stampRotation.toInt()}°"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbRot = SeekBar(act).apply { max = 360; progress = cv.stampRotation.toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cv.stampRotation = p.toFloat(); tvRot.text = "Rotation Angle: $p°" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}
                layout.addView(tvScaleX);addSeekBarWithNumberInput(act,layout,sbScaleX,10,500){cv.stampScaleX=it/100f;tvScaleX.text="Width Scale: $it%"};layout.addView(tvScaleY);addSeekBarWithNumberInput(act,layout,sbScaleY,10,500){cv.stampScaleY=it/100f;tvScaleY.text="Height Scale: $it%"};layout.addView(tvRot);addSeekBarWithNumberInput(act,layout,sbRot,0,360){cv.stampRotation=it.toFloat();tvRot.text="Rotation Angle: ${it}°"}

                MaterialAlertDialogBuilder(act)
                    .setTitle(configTitle("Image Stamp"))
                    .setView(scroll)
                    .setPositiveButton("Save", null)
                    .setNeutralButton("Use Stamp") { d, _ ->
						cv.useStamp()
						d.dismiss()
					}
                    .show()
            }
        }
    }

    private fun addSeekBarWithNumberInput(act: Activity, parent: LinearLayout, seekBar: SeekBar, minVal: Int, maxVal: Int, onValueChanged: ((Int) -> Unit)? = null) {
        val d=act.resources.displayMetrics.density
        val row=LinearLayout(act).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        seekBar.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        val input=EditText(act).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;setText((seekBar.progress+minVal).coerceIn(minVal,maxVal).toString());setTextColor(Color.WHITE);setSingleLine(true);gravity=Gravity.CENTER;setSelectAllOnFocus(true);layoutParams=LinearLayout.LayoutParams((58*d).toInt(),(40*d).toInt()).apply{leftMargin=(8*d).toInt()}}
        var updating=false
        fun setValue(v:Int){val value=v.coerceIn(minVal,maxVal);if(updating)return;updating=true;seekBar.progress=value-minVal;input.setText(value.toString());input.setSelection(input.text.length);onValueChanged?.invoke(value);updating=false}
        seekBar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){if(updating)return;val value=(p+minVal).coerceIn(minVal,maxVal);updating=true;input.setText(value.toString());input.setSelection(input.text.length);onValueChanged?.invoke(value);updating=false};override fun onStartTrackingTouch(s:SeekBar?){ };override fun onStopTrackingTouch(s:SeekBar?){ }})
        input.setOnEditorActionListener{_,_,_->setValue(input.text.toString().toIntOrNull()?:minVal);false}
        input.setOnFocusChangeListener{_,hasFocus->if(!hasFocus)setValue(input.text.toString().toIntOrNull()?:minVal)}
        input.addTextChangedListener(object:android.text.TextWatcher{override fun afterTextChanged(e:android.text.Editable?){if(!updating&&!e.isNullOrEmpty())e.toString().toIntOrNull()?.let{setValue(it)}};override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){}})
        row.addView(seekBar);row.addView(input);parent.addView(row)
    }

    private fun showSliderDialog(act: Activity,labelText:String,currentProgress:Int,minVal:Int,maxVal:Int,dialogTitle:String,onProgressChangedAction:(Int)->Unit){
        val d=act.resources.displayMetrics.density
        val scroll=ScrollView(act)
        val root=LinearLayout(act).apply{orientation=LinearLayout.VERTICAL;setPadding((24*d).toInt(),(16*d).toInt(),(24*d).toInt(),(16*d).toInt())}
        scroll.addView(root)
        val tv=TextView(act).apply{text="$labelText: ${currentProgress.coerceIn(minVal,maxVal)}";setTextColor(Color.WHITE)}
        val sb=SeekBar(act).apply{max=maxVal-minVal;progress=(currentProgress-minVal).coerceIn(0,maxVal-minVal)}
        root.addView(tv)
        addSeekBarWithNumberInput(act,root,sb,minVal,maxVal){onProgressChangedAction(it);tv.text="$labelText: $it"}
        MaterialAlertDialogBuilder(act).setTitle(dialogTitle).setView(scroll).setPositiveButton("OK",null).show()
    }

    fun showResolutionDialog(act: Activity, cv: InteractiveCanvasView) {
        val density = act.resources.displayMetrics.density
        val scroll = ScrollView(act)
        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
        scroll.addView(root)
        val etWidth = EditText(act).apply { hint = "Width"; setInputType(android.text.InputType.TYPE_CLASS_NUMBER); setText(cv.canvasWidth.toString()) }
        val etHeight = EditText(act).apply { hint = "Height"; setInputType(android.text.InputType.TYPE_CLASS_NUMBER); setText(cv.canvasHeight.toString()) }
        root.addView(etWidth); root.addView(etHeight)

        MaterialAlertDialogBuilder(act).setTitle("Resize Canvas Configuration").setView(scroll).setPositiveButton("Resize") { d, _ ->
            try {
                val w = etWidth.text.toString().trim().toInt().coerceIn(100, 4096)
                val h = etHeight.text.toString().trim().toInt().coerceIn(100, 4096)
                cv.resizeCanvasFromCenter(w, h)
            } catch(e: Exception) {}
            d.dismiss()
        }.setNegativeButton("Cancel", null).show()
    }

    fun showColorPickerDialog(act: Activity, cv: InteractiveCanvasView, imgColor: ImageView) {
        if (act.isFinishing || act.isDestroyed) return
        val d = act.resources.displayMetrics.density
        val outerScroll = ScrollView(act)
        val container = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        outerScroll.addView(container)

        val prefs = act.getSharedPreferences("canvas_prefs", Activity.MODE_PRIVATE)
        if (recentColorsList.isEmpty()) {
            val savedColorsStr = prefs.getString("recent_colors_palette", "") ?: ""
            if (savedColorsStr.isNotEmpty()) {
                savedColorsStr.split(",").forEach { 
                    try { recentColorsList.add(it.toInt()) } catch(e: Exception){}
                }
            }
        }

        val tabLayout = TabLayout(act).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { gravity = Gravity.CENTER }; addTab(newTab().setText("Presets")); addTab(newTab().setText("Recent")); addTab(newTab().setText("HSV Pad")); addTab(newTab().setText("HEX")) }
        container.addView(tabLayout)
        val contentFrame = FrameLayout(act).apply { layoutParams = LinearLayout.LayoutParams(-1, (240 * d).toInt()); setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt()) }
        container.addView(contentFrame)

        var currentSelectedColor = cv.brushColor
        var activeAlpha = Color.alpha(cv.brushColor)
        val hsv = FloatArray(3)
        Color.colorToHSV(currentSelectedColor, hsv)

        val bottomRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt()) }
        val currentBox = View(act).apply { layoutParams = LinearLayout.LayoutParams((80 * d).toInt(), (40 * d).toInt()).apply { rightMargin = (24 * d).toInt() }; setBackgroundColor(cv.brushColor) }
        val selectedBox = View(act).apply { layoutParams = LinearLayout.LayoutParams((80 * d).toInt(), (40 * d).toInt()); setBackgroundColor(cv.brushColor) }
        bottomRow.addView(TextView(act).apply { text = "Current: "; setTextColor(Color.WHITE) })
        bottomRow.addView(currentBox)
        bottomRow.addView(TextView(act).apply { text = "Selected: "; setTextColor(Color.WHITE) })
        bottomRow.addView(selectedBox)
        container.addView(bottomRow)

        fun updateTabContent(position: Int) {
            contentFrame.removeAllViews()
            when (position) {
                0 -> {
                    val grid = GridLayout(act).apply { columnCount = 4; layoutParams = FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER } }
                    val colors = intArrayOf(Color.BLACK, Color.WHITE, Color.GRAY, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.DKGRAY, Color.LTGRAY, Color.TRANSPARENT)
                    for (c in colors) {
                        val circle = View(act).apply {
                            layoutParams = GridLayout.LayoutParams().apply { width = (44 * d).toInt(); height = (44 * d).toInt(); setMargins((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt()) }
                            val baseBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke((1 * d).toInt(), 0xFF666666.toInt()) }
                            val rippleColor = android.content.res.ColorStateList.valueOf(if (c == Color.WHITE) 0x22000000 else 0x44FFFFFF)
                            background = android.graphics.drawable.RippleDrawable(rippleColor, baseBg, null)
                            isClickable = true
                            isFocusable = true
                            setOnClickListener { currentSelectedColor = c; selectedBox.setBackgroundColor(c) }
                        }
                        grid.addView(circle)
                    }
                    contentFrame.addView(grid)
                }
                1 -> {
                    if (recentColorsList.isEmpty()) {
                        contentFrame.addView(TextView(act).apply { text = "No recent colors yet."; setTextColor(Color.GRAY); gravity = Gravity.CENTER })
                    } else {
                        val recentScroll = ScrollView(act).apply { 
                            layoutParams = FrameLayout.LayoutParams(-1, -1)
                            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
                            setClipToPadding(false)
                        }
                        val recentGrid = GridLayout(act).apply { columnCount = 4; layoutParams = FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL } }
                        recentColorsList.forEach { c ->
                            val circle = View(act).apply {
                                layoutParams = GridLayout.LayoutParams().apply { width = (44 * d).toInt(); height = (44 * d).toInt(); setMargins((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt()) }
                                val baseBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke((1 * d).toInt(), 0xFF666666.toInt()) }
                                val rippleColor = android.content.res.ColorStateList.valueOf(if (c == Color.WHITE) 0x22000000 else 0x44FFFFFF)
                                background = android.graphics.drawable.RippleDrawable(rippleColor, baseBg, null)
                                isClickable = true
                                isFocusable = true
                                setOnClickListener { currentSelectedColor = c; selectedBox.setBackgroundColor(c) }
                                setOnLongClickListener {
                                    MaterialAlertDialogBuilder(act)
                                        .setTitle("Delete Color")
                                        .setMessage("Remove this color from recent palette?")
                                        .setPositiveButton("Delete") { _, _ ->
                                            recentColorsList.remove(c)
                                            val outStr = recentColorsList.joinToString(",")
                                            prefs.edit().putString("recent_colors_palette", outStr).apply()
                                            updateTabContent(1)
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                    true
                                }
                            }
                            recentGrid.addView(circle)
                        }
                        recentScroll.addView(recentGrid)
                        contentFrame.addView(recentScroll)
                    }
                }
                2 -> {
                    val rootHsv = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                    val sbHue = SeekBar(act).apply { max = 360; progress = hsv[0].toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { hsv[0] = p.toFloat(); currentSelectedColor = Color.HSVToColor(activeAlpha, hsv); selectedBox.setBackgroundColor(currentSelectedColor) }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })}
                    val sbSat = SeekBar(act).apply { max = 100; progress = (hsv[1] * 100).toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { hsv[1] = p / 100f; currentSelectedColor = Color.HSVToColor(activeAlpha, hsv); selectedBox.setBackgroundColor(currentSelectedColor) }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })}
                    val sbVal = SeekBar(act).apply { max = 100; progress = (hsv[2] * 100).toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { hsv[2] = p / 100f; currentSelectedColor = Color.HSVToColor(activeAlpha, hsv); selectedBox.setBackgroundColor(currentSelectedColor) }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })}
                    val sbAlpha = SeekBar(act).apply { max = 255; progress = activeAlpha; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { activeAlpha = p; currentSelectedColor = Color.HSVToColor(activeAlpha, hsv); selectedBox.setBackgroundColor(currentSelectedColor) }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })}
                    rootHsv.addView(TextView(act).apply { text = "Hue"; setTextColor(Color.WHITE) }); rootHsv.addView(sbHue)
                    rootHsv.addView(TextView(act).apply { text = "Saturation"; setTextColor(Color.WHITE) }); rootHsv.addView(sbSat)
                    rootHsv.addView(TextView(act).apply { text = "Value"; setTextColor(Color.WHITE) }); rootHsv.addView(sbVal)
                    rootHsv.addView(TextView(act).apply { text = "Alpha"; setTextColor(Color.WHITE) }); rootHsv.addView(sbAlpha)
                    contentFrame.addView(rootHsv)
                }
                3 -> {
                    val hexLayout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                    val et = EditText(act).apply { 
                        hint = "#FFFFFFFF"
                        setText(String.format("#%08X", currentSelectedColor))
                        isFocusableInTouchMode = true
                        inputType = android.text.InputType.TYPE_CLASS_TEXT
                        setSingleLine(true)
                    }
                    et.addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(s: android.text.Editable?) { try { val c = Color.parseColor(s.toString().trim()); currentSelectedColor = c; Color.colorToHSV(c, hsv); activeAlpha = Color.alpha(c); selectedBox.setBackgroundColor(c) } catch (e: Exception) {} }
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    })
                    hexLayout.addView(et)
                    contentFrame.addView(hexLayout)

                    et.requestFocus()
                    et.postDelayed({
                        val imm = act.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
                    }, 200)
                }
            }
        }

        val paletteDialog = MaterialAlertDialogBuilder(act)
            .setTitle("Palette Configuration")
            .setView(outerScroll)
            .setPositiveButton("Apply") { _, _ -> 
                cv.brushColor = currentSelectedColor
                imgColor.setColorFilter(currentSelectedColor, android.graphics.PorterDuff.Mode.SRC_IN)
                
                if (recentColorsList.contains(currentSelectedColor)) {
                    recentColorsList.remove(currentSelectedColor)
                }
                
                recentColorsList.add(0, currentSelectedColor)
                
                val outStr = recentColorsList.joinToString(",")
                prefs.edit().putString("recent_colors_palette", outStr).apply()
            }
            .setNegativeButton("Cancel", null)
            .create()

        paletteDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        paletteDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { 
                if (tab.position != 3) {
                    val imm = act.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(tabLayout.windowToken, 0)
                }
                updateTabContent(tab.position) 
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        updateTabContent(0)
        paletteDialog.show()
    }
}
