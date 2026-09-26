package com.bitoneko.kouecanvas
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import java.util.LinkedList
import java.util.Random
enum class SelectionMode { SINGLE_SELECT, MULTI_SELECT }
enum class ToolMode {
    BRUSH,
    ERASER,
    AIRBRUSH,
    BLUR,
    SMUDGE,
    FILL,
    GRADIENT,
    PIPPETE,
    PAN,
    LINE,
    RECTANGLE,
    CIRCLE,
    ARROW,
    SELECT_RECT,
    LASSO,
    STAMP_TEXT,
    STAMP_IMAGE,
    TRANSFORM,
    PEN_VECTOR,
    CROP,
    SYMMETRY,
    COLOR_ADJUST
}
enum class GradientType { LINEAR, RADIAL }
enum class SymmetryMode { NONE, HORIZONTAL, VERTICAL, RADIAL }
class InteractiveCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object {
        const val MAX_CANVAS_SIZE = 4096
        const val MIN_CANVAS_SIZE = 1
        const val MAX_BITMAP_PIXELS = MAX_CANVAS_SIZE.toLong() * MAX_CANVAS_SIZE.toLong()
    }
    interface CanvasHistoryListener {
        fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean)
    }
    interface ColorPickedListener {
        fun onColorPicked(color: Int)
    }
    sealed class DrawAction {
        data class BaseImage(
            val layerId: String,
            val bitmap: Bitmap
        ) : DrawAction()
        data class Line(
            val layerId: String,
            val path: Path,
            val color: Int,
            val width: Float,
            val isEraser: Boolean
        ) : DrawAction()
        data class Fill(
            val layerId: String,
            val x: Int,
            val y: Int,
            val color: Int
        ) : DrawAction()
        data class TextStamp(
            val layerId: String,
            val text: String,
            val x: Float,
            val y: Float,
            val size: Float,
            val rotation: Float,
            val color: Int,
            val typeface: Typeface?
        ) : DrawAction()
        data class ImageStamp(
            val layerId: String,
            val bitmap: Bitmap,
            val x: Float,
            val y: Float,
            val scaleX: Float,
            val scaleY: Float,
            val rotation: Float
        ) : DrawAction()
        data class DrawShape(
            val layerId: String,
            val tool: ToolMode,
            val sx: Float,
            val sy: Float,
            val cx: Float,
            val cy: Float,
            val color: Int,
            val width: Float
        ) : DrawAction()
        data class TransformLayer(
            val layerId: String,
            val matrix: Matrix
        ) : DrawAction()
        data class ApplyFilter(
            val layerId: String,
            val resultBitmap: Bitmap
        ) : DrawAction()
        data class CanvasResize(
            val beforeWidth: Int,
            val beforeHeight: Int,
            val afterWidth: Int,
            val afterHeight: Int,
            val beforeSnapshots: Map<String, Bitmap>,
            val afterSnapshots: Map<String, Bitmap>
        ) : DrawAction()
        data class AddLayer(
            val layerId: String,
            val name: String
        ) : DrawAction()
        data class DeleteLayer(
            val layerId: String,
            val name: String,
            val cachedBitmapSnapshot: Bitmap
        ) : DrawAction()
        data class LayerState(
    val items: ArrayList<LayerStateItem>,
    val activeLayerIndex: Int
)
data class LayerStateChange(
    val before: LayerState,
    val after: LayerState
) : DrawAction()
data class LayerStateItem(
    val id: String,
    val name: String,
    val bitmap: Bitmap,
    val alpha: Int,
    val isVisible: Boolean
)
    }
    var historyListener: CanvasHistoryListener? = null
    var colorPickedListener: ColorPickedListener? = null
    var currentTool = ToolMode.BRUSH
        set(value) {
            if (field != value) {
                if (isSelectionActive ||
                    field == ToolMode.SELECT_RECT ||
                    field == ToolMode.LASSO
                ) {
                    clearSelection()
                }
                if (field == ToolMode.TRANSFORM) {
                    applyTransformToActiveLayer()
                }
                if (field == ToolMode.PEN_VECTOR) {
                    vectorPath.reset()
                    vectorPoints.clear()
                }
                field = value
                if (field == ToolMode.STAMP_IMAGE ||
                    field == ToolMode.STAMP_TEXT
                ) {
                    stampScaleX = 1f
                    stampScaleY = 1f
                    stampRotation = 0f
                    initStampFrame()
                }
                if (field == ToolMode.TRANSFORM) {
                    initTransformForActiveLayer()
                }
                if (field == ToolMode.CROP) {
                    cropRect.set(
                        0f,
                        0f,
                        canvasWidth.toFloat(),
                        canvasHeight.toFloat()
                    )
                }
                invalidate()
            }
        }
    var selectionMode: SelectionMode = SelectionMode.SINGLE_SELECT
    private val selectedPathsQueue = LinkedList<Path>()
    var brushColor = Color.BLACK
    var brushSize = 10f
    var eraserSize = 20f
    var fillTolerance = 15
    var smudgeStrength = 0.5f
    var stampText = "Smilemoon"
        set(value) {
            field = value
            if (currentTool == ToolMode.STAMP_TEXT) {
                updateStampBoundsOnly()
                updateStampMatrix()
                invalidate()
            }
        }
    var stampTextSize = 40f
        set(value) {
            field = value
            if (currentTool == ToolMode.STAMP_TEXT) {
                updateStampFrameFromParams()
            }
        }
    var stampTypeface: Typeface? = Typeface.DEFAULT
        set(value) {
            field = value
            if (currentTool == ToolMode.STAMP_TEXT) {
                updateStampBoundsOnly()
                updateStampMatrix()
                invalidate()
            }
        }
    var stampBitmap: Bitmap? = null
        set(value) {
            field = value
            if (currentTool == ToolMode.STAMP_IMAGE) {
                updateStampBoundsOnly()
                updateStampMatrix()
                invalidate()
            }
        }
    private var internalStampUpdate = false
    private var _stampScaleX = 1.0f
    var stampScaleX: Float
        get() = _stampScaleX
        set(value) {
            _stampScaleX = value.coerceAtLeast(0.01f)
            if (!internalStampUpdate &&
                currentTool == ToolMode.STAMP_IMAGE
            ) {
                updateStampFrameFromParams()
            }
        }
    private var _stampScaleY = 1.0f
    var stampScaleY: Float
        get() = _stampScaleY
        set(value) {
            _stampScaleY = value.coerceAtLeast(0.01f)
            if (!internalStampUpdate &&
                currentTool == ToolMode.STAMP_IMAGE
            ) {
                updateStampFrameFromParams()
            }
        }
    private var _stampRotation = 0f
    var stampRotation: Float
        get() = _stampRotation
        set(value) {
            _stampRotation = value
            if (!internalStampUpdate &&
                (currentTool == ToolMode.STAMP_IMAGE ||
                    currentTool == ToolMode.STAMP_TEXT)
            ) {
                updateStampFrameFromParams()
            }
        }
    private val stampBounds = RectF()
    private val stampMatrix = Matrix()
    private var stampCenterX = 0f
    private var stampCenterY = 0f
    var gradientType = GradientType.LINEAR
    var gradientEndColor = Color.TRANSPARENT
    var symmetryMode = SymmetryMode.NONE
    var symmetryRadialSectors = 4
    val vectorPoints = ArrayList<PointF>()
    val vectorPath = Path()
    var cropRect = RectF()
    private var cropRotation = 0f
    val layerTransformMatrix = Matrix()
    private var activeHandle = -1
    private val transformBounds = RectF()
    var layersList = ArrayList<CanvasLayer>()
    var activeLayerIndex = 0
    private var layerHistoryBefore: DrawAction.LayerState? = null
    var canvasWidth = 500
    var canvasHeight = 500
    private var projectId: String = ""
    private var projectName: String = "New Canvas Project"
    var isRotationEnabled = true
    var isMagnifierEnabled = false
    private val canvasMatrix = Matrix()
    private val invertedMatrix = Matrix()
    private var prevFocusX = 0f
    private var prevFocusY = 0f
    private var isTransforming = false
    private var lastAngle = 0f
    private var isTouchingCanvas = false
    private var touchRawX = 0f
    private var touchRawY = 0f
    private val magnifierRadius = 160f
    private val magnifierMarginLeft = 40f
    private val magnifierMarginTop = 40f
    private val magnifierFactor = 2.0f
    private val magnifierPath = Path()
    private val magnifierMatrix = Matrix()
    private val magnifierBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val magnifierCrosshairPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val undoActions = ArrayList<DrawAction>()
    private val redoActions = ArrayList<DrawAction>()
    val currentDrawPath = Path()
    private var checkerPaint = Paint()
    private val layerBlendPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        isFilterBitmap = true
    }
    private val borderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawingShape = false
    private var selectionPath = Path()
    private var isSelectionActive = false
    private var clipboardBitmap: Bitmap? = null
    private val randomGen = Random()
    private var lastSmudgePx = -1
    private var lastSmudgePy = -1
    private val accentColor = Color.parseColor("#FFFF7A7A")
    private val rotationHandleColor = Color.parseColor("#FF4CAF50")
    private val selectionPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }
    private val selectionBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val transformFramePaint = Paint().apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val transformHandlePaint = Paint().apply {
        color = accentColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val rotationHandlePaint = Paint().apply {
        color = rotationHandleColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val symmetryGuidePaint = Paint().apply {
        color = Color.parseColor("#FF0055")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        isAntiAlias = true
    }
    val drawPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                val focusX = detector.focusX
                val focusY = detector.focusY
                val currentScale = getCurrentScale()
                val targetScale = currentScale * scale
                if (targetScale in 0.05f..30.0f) {
                    canvasMatrix.postScale(
                        scale,
                        scale,
                        focusX,
                        focusY
                    )
                    invalidate()
                }
                return true
            }
        }
    )
    private val saveExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor()
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initCheckerboardPattern()
        loadSettingsFromPreferences()
    }
    private fun isValidCanvasSize(width: Int, height: Int): Boolean {
        return width in MIN_CANVAS_SIZE..MAX_CANVAS_SIZE &&
            height in MIN_CANVAS_SIZE..MAX_CANVAS_SIZE &&
            width.toLong() * height.toLong() <= MAX_BITMAP_PIXELS
    }
    private fun clampCanvasDimension(value: Int): Int {
        return value.coerceIn(MIN_CANVAS_SIZE, MAX_CANVAS_SIZE)
    }
    private fun createSafeBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        if (!isValidCanvasSize(width, height)) {
            return null
        }
        return try {
            Bitmap.createBitmap(
                width,
                height,
                config
            )
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    private fun copyBitmapSafely(bitmap: Bitmap): Bitmap? {
        return try {
            bitmap.copy(
                Bitmap.Config.ARGB_8888,
                true
            )
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    fun loadSettingsFromPreferences() {
        val prefs = context.getSharedPreferences(
            "canvas_settings",
            Context.MODE_PRIVATE
        )
        isRotationEnabled = prefs.getBoolean(
            "enable_pinch_rotate",
            false
        )
        isMagnifierEnabled = prefs.getBoolean(
            "enable_magnifier",
            false
        )
        val isMultiSelectEnabled = prefs.getBoolean(
            "enable_select_mode",
            false
        )
        selectionMode = if (isMultiSelectEnabled) {
            SelectionMode.MULTI_SELECT
        } else {
            SelectionMode.SINGLE_SELECT
        }
    }
    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(
            w,
            h,
            oldw,
            oldh
        )
        if (oldw == 0 && oldh == 0) {
            centerCanvasInView()
        } else if (w != oldw || h != oldh) {
            val dx = (w - oldw) / 2f
            val dy = (h - oldh) / 2f
            canvasMatrix.postTranslate(
                dx,
                dy
            )
            invalidate()
        }
    }
    fun centerCanvasInView() {
        if (width == 0 ||
            height == 0 ||
            canvasWidth <= 0 ||
            canvasHeight <= 0
        ) {
            return
        }
        canvasMatrix.reset()
        val scale = Math.min(
            width.toFloat() / canvasWidth,
            height.toFloat() / canvasHeight
        ) * 0.8f
        val dx = (width - canvasWidth * scale) / 2f
        val dy = (height - canvasHeight * scale) / 2f
        canvasMatrix.postScale(
            scale,
            scale
        )
        canvasMatrix.postTranslate(
            dx,
            dy
        )
        invalidate()
    }
    private fun getCurrentScale(): Float {
        val values = FloatArray(9)
        canvasMatrix.getValues(values)
        val scaleX = values[Matrix.MSCALE_X]
        val skewY = values[Matrix.MSKEW_Y]
        return Math.sqrt(
            (
                scaleX * scaleX +
                    skewY * skewY
                ).toDouble()
        ).toFloat()
    }
    fun setProjectName(name: String) {
        this.projectName = name
        saveCurrentStateToDiskAsync()
    }
    private fun initCheckerboardPattern() {
        val size = 16
        val bitmap = Bitmap.createBitmap(
            size * 2,
            size * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val p = Paint()
        p.color = Color.parseColor("#FFFFFFFF")
        canvas.drawRect(
            0f,
            0f,
            size.toFloat(),
            size.toFloat(),
            p
        )
        canvas.drawRect(
            size.toFloat(),
            size.toFloat(),
            (size * 2).toFloat(),
            (size * 2).toFloat(),
            p
        )
        p.color = Color.parseColor("#FFDDDDDD")
        canvas.drawRect(
            size.toFloat(),
            0f,
            (size * 2).toFloat(),
            size.toFloat(),
            p
        )
        canvas.drawRect(
            0f,
            size.toFloat(),
            size.toFloat(),
            (size * 2).toFloat(),
            p
        )
        checkerPaint.shader = BitmapShader(
            bitmap,
            Shader.TileMode.REPEAT,
            Shader.TileMode.REPEAT
        )
    }
    fun initCanvasProject(
        id: String,
        name: String,
        width: Int,
        height: Int
    ) {
        if (!isValidCanvasSize(width, height)) {
            return
        }
        this.projectId = id
        this.projectName = name
        this.canvasWidth = width
        this.canvasHeight = height
        val preservedActiveLayerId =
            layersList.getOrNull(activeLayerIndex)?.id
        layersList.forEach {
            if (!it.bitmap.isRecycled) {
                it.bitmap.recycle()
            }
        }
        layersList.clear()
        undoActions.clear()
        redoActions.clear()
        val pFile = File(
            context.filesDir,
            "preview_$id.png"
        )
        if (pFile.exists()) {
            val opt = BitmapFactory.Options().apply {
                inMutable = true
            }
            val savedBmp = try {
                BitmapFactory.decodeFile(
                    pFile.absolutePath,
                    opt
                )
            } catch (e: OutOfMemoryError) {
                null
            }
            if (savedBmp != null) {
                if (!isValidCanvasSize(
                        savedBmp.width,
                        savedBmp.height
                    )
                ) {
                    savedBmp.recycle()
                    return
                }
                this.canvasWidth = savedBmp.width
                this.canvasHeight = savedBmp.height
                layersList.add(
                    CanvasLayer(
                        "layer_0",
                        "Layer 1",
                        savedBmp
                    )
                )
                val historyBitmap = copyBitmapSafely(savedBmp)
                if (historyBitmap != null) {
                    undoActions.add(
                        DrawAction.BaseImage(
                            "layer_0",
                            historyBitmap
                        )
                    )
                }
            }
        }
        if (layersList.isEmpty()) {
            val defaultBmp = createSafeBitmap(
                this.canvasWidth,
                this.canvasHeight
            ) ?: return
            Canvas(defaultBmp).drawColor(
                Color.WHITE
            )
            layersList.add(
                CanvasLayer(
                    "layer_0",
                    "Layer 1",
                    defaultBmp
                )
            )
            val historyBitmap = copyBitmapSafely(defaultBmp)
            if (historyBitmap != null) {
                undoActions.add(
                    DrawAction.BaseImage(
                        "layer_0",
                        historyBitmap
                    )
                )
            }
        }
        activeLayerIndex = 0
        cropRect.set(
            0f,
            0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat()
        )
        cropRotation = 0f
        resetStampTransform()
        layerTransformMatrix.reset()
        centerCanvasInView()
        notifyHistoryListener()
        invalidate()
    }
    fun forceSetDimensionsAndBitmap(
        w: Int,
        h: Int,
        bmp: Bitmap
    ) {
        if (!isValidCanvasSize(w, h) ||
            bmp.isRecycled ||
            bmp.width != w ||
            bmp.height != h
        ) {
            return
        }
        this.canvasWidth = w
        this.canvasHeight = h
        layersList.forEach {
            if (!it.bitmap.isRecycled) {
                it.bitmap.recycle()
            }
        }
        layersList.clear()
        layersList.add(
            CanvasLayer(
                "layer_0",
                "Layer 1",
                bmp
            )
        )
        activeLayerIndex = 0
        cropRect.set(
            0f,
            0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat()
        )
        cropRotation = 0f
        undoActions.clear()
        redoActions.clear()
        val historyBitmap = copyBitmapSafely(bmp)
        if (historyBitmap != null) {
            undoActions.add(
                DrawAction.BaseImage(
                    "layer_0",
                    historyBitmap
                )
            )
        }
        resetStampTransform()
        layerTransformMatrix.reset()
        centerCanvasInView()
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
        invalidate()
    }
    fun resizeCanvasFromCenter(
        newW: Int,
        newH: Int
    ) {
        if (!isValidCanvasSize(newW, newH) ||
            layersList.isEmpty() ||
            (newW == canvasWidth && newH == canvasHeight)
        ) {
            return
        }
        val beforeWidth = canvasWidth
        val beforeHeight = canvasHeight
        val beforeSnapshots = HashMap<String, Bitmap>()
        for (layer in layersList) {
            val snapshot = copyBitmapSafely(layer.bitmap)
                ?: run {
                    beforeSnapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    return
                }
            beforeSnapshots[layer.id] = snapshot
        }
        val dx = (newW - canvasWidth) / 2f
        val dy = (newH - canvasHeight) / 2f
        val newBitmaps = ArrayList<Bitmap>()
        for (layer in layersList) {
            val newBmp = createSafeBitmap(
                newW,
                newH
            ) ?: run {
                newBitmaps.forEach {
                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }
                beforeSnapshots.values.forEach {
                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }
                return
            }
            Canvas(newBmp).drawBitmap(
                layer.bitmap,
                dx,
                dy,
                null
            )
            newBitmaps.add(newBmp)
        }
        val afterSnapshots = HashMap<String, Bitmap>()
        for (index in layersList.indices) {
            val layer = layersList[index]
            val snapshot = copyBitmapSafely(newBitmaps[index])
                ?: run {
                    newBitmaps.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    beforeSnapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    afterSnapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    return
                }
            afterSnapshots[layer.id] = snapshot
        }
        layersList.forEachIndexed { index, layer ->
            if (!layer.bitmap.isRecycled) {
                layer.bitmap.recycle()
            }
            layer.bitmap = newBitmaps[index]
        }
        this.canvasWidth = newW
        this.canvasHeight = newH
        undoActions.add(
            DrawAction.CanvasResize(
                beforeWidth,
                beforeHeight,
                newW,
                newH,
                beforeSnapshots,
                afterSnapshots
            )
        )
        cropRect.set(
            0f,
            0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat()
        )
        cropRotation = 0f
        resetStampTransform()
        layerTransformMatrix.reset()
        redoActions.clear()
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
        invalidate()
    }
    private fun resetStampTransform() {
        stampMatrix.reset()
        stampCenterX = canvasWidth / 2f
        stampCenterY = canvasHeight / 2f
        internalStampUpdate = true
        _stampScaleX = 1f
        _stampScaleY = 1f
        _stampRotation = 0f
        internalStampUpdate = false
    }
    private fun getAngle(event: MotionEvent): Float {
        val deltaX = (
            event.getX(0) -
                event.getX(1)
            ).toDouble()
        val deltaY = (
            event.getY(0) -
                event.getY(1)
            ).toDouble()
        val radians = Math.atan2(
            deltaY,
            deltaX
        )
        return Math.toDegrees(
            radians
        ).toFloat()
    }
    private fun isStrokeTool(
        tool: ToolMode
    ): Boolean {
        return tool == ToolMode.BRUSH ||
            tool == ToolMode.ERASER ||
            tool == ToolMode.AIRBRUSH ||
            tool == ToolMode.BLUR ||
            tool == ToolMode.SMUDGE ||
            tool == ToolMode.PEN_VECTOR
    }
    private fun updateStampBoundsOnly() {
        if (currentTool == ToolMode.STAMP_IMAGE) {
            stampBitmap?.let { bmp ->
                val w = bmp.width.toFloat()
                val h = bmp.height.toFloat()
                stampBounds.set(
                    -w / 2f,
                    -h / 2f,
                    w / 2f,
                    h / 2f
                )
            } ?: run {
                stampBounds.set(-100f, -100f, 100f, 100f)
            }
        } else if (currentTool == ToolMode.STAMP_TEXT) {
            val tPaint = Paint().apply {
                textSize = stampTextSize
                typeface = stampTypeface
                isAntiAlias = true
            }
            val lines = stampText.split("\n")
            var maxW = 0f
            lines.forEach { line ->
                maxW = maxOf(maxW, tPaint.measureText(line))
            }
            val totalH = lines.size * tPaint.fontSpacing
            stampBounds.set(
                -maxW / 2f,
                -totalH / 2f,
                maxW / 2f,
                totalH / 2f
            )
        }
    }
    private fun initStampFrame() {
        val screenCenterX = width / 2f
        val screenCenterY = height / 2f
        val inv = Matrix()
        if (canvasMatrix.invert(inv)) {
            val pts = floatArrayOf(
                screenCenterX,
                screenCenterY
            )
            inv.mapPoints(pts)
            stampCenterX = pts[0]
            stampCenterY = pts[1]
        } else {
            stampCenterX = canvasWidth / 2f
            stampCenterY = canvasHeight / 2f
        }
        updateStampBoundsOnly()
        updateStampMatrix()
    }
    private fun updateStampMatrix() {
        stampMatrix.reset()
        stampMatrix.postScale(
            _stampScaleX,
            _stampScaleY
        )
        stampMatrix.postRotate(
            _stampRotation
        )
        stampMatrix.postTranslate(
            stampCenterX,
            stampCenterY
        )
    }
    private fun updateStampParamsFromMatrix() {
        val values = FloatArray(9)
        stampMatrix.getValues(values)
        stampCenterX =
            values[Matrix.MTRANS_X]
        stampCenterY =
            values[Matrix.MTRANS_Y]
        val scaleX = Math.hypot(
            values[Matrix.MSCALE_X].toDouble(),
            values[Matrix.MSKEW_Y].toDouble()
        ).toFloat()
        val scaleY = Math.hypot(
            values[Matrix.MSKEW_X].toDouble(),
            values[Matrix.MSCALE_Y].toDouble()
        ).toFloat()
        val rotation = Math.toDegrees(
            Math.atan2(
                values[Matrix.MSKEW_Y].toDouble(),
                values[Matrix.MSCALE_X].toDouble()
            )
        ).toFloat()
        internalStampUpdate = true
        _stampScaleX =
            scaleX.coerceAtLeast(0.01f)
        _stampScaleY =
            scaleY.coerceAtLeast(0.01f)
        _stampRotation = rotation
        internalStampUpdate = false
    }
    private fun updateStampFrameFromParams() {
        updateStampMatrix()
        invalidate()
    }
    fun useStamp() {
        if (activeLayerIndex >= layersList.size) {
            return
        }
        val activeLayer =
            layersList[activeLayerIndex]
        val layerCanvas =
            Canvas(activeLayer.bitmap)
        val currentLayerId =
            activeLayer.id
        if (currentTool == ToolMode.STAMP_IMAGE) {
            stampBitmap?.let { bmp ->
                withSelectionClip(layerCanvas) {
                    layerCanvas.save()
                    layerCanvas.concat(
                        stampMatrix
                    )
                    layerCanvas.drawBitmap(
                        bmp,
                        -bmp.width / 2f,
                        -bmp.height / 2f,
                        Paint(Paint.ANTI_ALIAS_FLAG)
                    )
                    layerCanvas.restore()
                }
                saveStrokeToHistory(
                    currentLayerId,
                    currentTool
                )
                invalidate()
            }
        } else if (currentTool == ToolMode.STAMP_TEXT) {
            val tPaint = Paint().apply {
                color = brushColor
                textSize = stampTextSize
                typeface = stampTypeface
                isAntiAlias = true
            }
            withSelectionClip(layerCanvas) {
                layerCanvas.save()
                layerCanvas.concat(
                    stampMatrix
                )
                val lines =
                    stampText.split("\n")
                var cy =
                    stampBounds.top +
                        tPaint.textSize
                lines.forEach { line ->
                    layerCanvas.drawText(
                        line,
                        stampBounds.left,
                        cy,
                        tPaint
                    )
                    cy +=
                        tPaint.fontSpacing
                }
                layerCanvas.restore()
            }
            saveStrokeToHistory(
                currentLayerId,
                currentTool
            )
            invalidate()
        }
    }
    override fun onTouchEvent(
        ev: MotionEvent
    ): Boolean {
        scaleDetector.onTouchEvent(ev)
        val pointerCount =
            ev.pointerCount
        touchRawX = ev.x
        touchRawY = ev.y
        if (pointerCount > 1 ||
            currentTool == ToolMode.PAN
        ) {
            isTouchingCanvas = false
            if (!isTransforming) {
                isTransforming = true
                currentDrawPath.reset()
                var sumX = 0f
                var sumY = 0f
                for (i in 0 until pointerCount) {
                    sumX += ev.getX(i)
                    sumY += ev.getY(i)
                }
                prevFocusX =
                    sumX / pointerCount
                prevFocusY =
                    sumY / pointerCount
                if (pointerCount > 1) {
                    lastAngle =
                        getAngle(ev)
                }
            }
            var sumX = 0f
            var sumY = 0f
            for (i in 0 until pointerCount) {
                sumX += ev.getX(i)
                sumY += ev.getY(i)
            }
            val focusX =
                sumX / pointerCount
            val focusY =
                sumY / pointerCount
            val dx =
                focusX - prevFocusX
            val dy =
                focusY - prevFocusY
            canvasMatrix.postTranslate(
                dx,
                dy
            )
            if (pointerCount > 1) {
                val currentAngle =
                    getAngle(ev)
                var deltaAngle =
                    currentAngle - lastAngle
                if (deltaAngle > 180f) {
                    deltaAngle -= 360f
                }
                if (deltaAngle < -180f) {
                    deltaAngle += 360f
                }
                if (isRotationEnabled) {
                    canvasMatrix.postRotate(
                        deltaAngle,
                        focusX,
                        focusY
                    )
                }
                lastAngle = currentAngle
            }
            prevFocusX = focusX
            prevFocusY = focusY
            invalidate()
            return true
        }
        if (ev.action == MotionEvent.ACTION_UP ||
            ev.action == MotionEvent.ACTION_CANCEL
        ) {
            isTouchingCanvas = false
            if (isTransforming) {
                isTransforming = false
                return true
            }
        }
        if (isTransforming) {
            return true
        }
        if (activeLayerIndex >= layersList.size) {
            return true
        }
        val activeLayer =
            layersList[activeLayerIndex]
        if (!activeLayer.isVisible &&
            currentTool != ToolMode.CROP
        ) {
            return true
        }
        if (!canvasMatrix.invert(
                invertedMatrix
            )
        ) {
            return true
        }
        val touchPts = floatArrayOf(
            ev.x,
            ev.y
        )
        invertedMatrix.mapPoints(
            touchPts
        )
        val modelX = touchPts[0]
        val modelY = touchPts[1]
        val layerCanvas =
            Canvas(activeLayer.bitmap)
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouchingCanvas =
                    isStrokeTool(currentTool)
                startX = modelX
                startY = modelY
                currentX = modelX
                currentY = modelY
                when (currentTool) {
                    ToolMode.TRANSFORM -> {
                        if (layerTransformMatrix.isIdentity) {
                            initTransformForActiveLayer()
                        }
                        activeHandle =
                            getInteractiveHandleAt(
                                transformBounds,
                                layerTransformMatrix,
                                modelX,
                                modelY,
                                hasTopRotationStem = true
                            )
                    }
                    ToolMode.CROP -> {
                        if (cropRect.isEmpty) {
                            cropRect.set(
                                0f,
                                0f,
                                canvasWidth.toFloat(),
                                canvasHeight.toFloat()
                            )
                        }
                        activeHandle =
                            getInteractiveHandleAt(
                                cropRect,
                                null,
                                modelX,
                                modelY,
                                isCropTool = true
                            )
                    }
                    ToolMode.STAMP_IMAGE,
                    ToolMode.STAMP_TEXT -> {
                        activeHandle =
                            getInteractiveHandleAt(
                                stampBounds,
                                stampMatrix,
                                modelX,
                                modelY,
                                hasTopRotationStem = true
                            )
                    }
                    ToolMode.PEN_VECTOR -> {
                        vectorPoints.add(
                            PointF(
                                modelX,
                                modelY
                            )
                        )
                        rebuildVectorPath()
                        invalidate()
                    }
                    ToolMode.SMUDGE -> {
                        lastSmudgePx =
                            modelX.toInt()
                        lastSmudgePy =
                            modelY.toInt()
                        currentDrawPath.reset()
                        currentDrawPath.moveTo(
                            modelX,
                            modelY
                        )
                    }
                    ToolMode.PIPPETE -> {
                        val px =
                            modelX.toInt()
                        val py =
                            modelY.toInt()
                        if (px in 0 until
                            activeLayer.bitmap.width &&
                            py in 0 until
                            activeLayer.bitmap.height
                        ) {
                            val color =
                                activeLayer.bitmap.getPixel(
                                    px,
                                    py
                                )
                            brushColor = color
                            colorPickedListener
                                ?.onColorPicked(color)
                        }
                    }
                    ToolMode.LINE,
                    ToolMode.RECTANGLE,
                    ToolMode.CIRCLE,
                    ToolMode.ARROW,
                    ToolMode.GRADIENT -> {
                        isDrawingShape = true
                    }
                    ToolMode.SELECT_RECT -> {
                        if (selectionMode ==
                            SelectionMode.SINGLE_SELECT ||
                            !isSelectionActive
                        ) {
                            selectionPath.reset()
                            selectedPathsQueue.clear()
                        }
                        isDrawingShape = true
                    }
                    ToolMode.LASSO -> {
                        if (selectionMode ==
                            SelectionMode.SINGLE_SELECT ||
                            !isSelectionActive
                        ) {
                            selectionPath.reset()
                            selectedPathsQueue.clear()
                        }
                        currentDrawPath.reset()
                        currentDrawPath.moveTo(
                            modelX,
                            modelY
                        )
                        isDrawingShape = true
                    }
                    ToolMode.AIRBRUSH,
                    ToolMode.BLUR -> {
                        setupPaintForCurrentTool()
                        currentDrawPath.reset()
                        currentDrawPath.moveTo(
                            modelX,
                            modelY
                        )
                        applyAirbrushEffect(
                            layerCanvas,
                            modelX,
                            modelY
                        )
                        invalidate()
                    }
                    ToolMode.BRUSH,
                    ToolMode.ERASER -> {
                        setupPaintForCurrentTool()
                        currentDrawPath.reset()
                        currentDrawPath.moveTo(
                            modelX,
                            modelY
                        )
                        invalidate()
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx =
                    modelX - currentX
                val dy =
                    modelY - currentY
                currentX = modelX
                currentY = modelY
                when (currentTool) {
                    ToolMode.TRANSFORM -> {
                        updateInteractiveTransform(
                            activeHandle,
                            transformBounds,
                            layerTransformMatrix,
                            modelX,
                            modelY,
                            dx,
                            dy
                        )
                        invalidate()
                    }
                    ToolMode.CROP -> {
                        updateCropRect(
                            activeHandle,
                            modelX,
                            modelY,
                            dx,
                            dy
                        )
                        invalidate()
                    }
                    ToolMode.STAMP_IMAGE,
                    ToolMode.STAMP_TEXT -> {
                        updateInteractiveTransform(
                            activeHandle,
                            stampBounds,
                            stampMatrix,
                            modelX,
                            modelY,
                            dx,
                            dy,
                            hasTopRotationStem = true
                        )
                        updateStampParamsFromMatrix()
                        invalidate()
                    }
                    ToolMode.PEN_VECTOR -> {
                        if (vectorPoints.isNotEmpty()) {
                            vectorPoints[
                                vectorPoints.size - 1
                            ].set(
                                modelX,
                                modelY
                            )
                            rebuildVectorPath()
                            invalidate()
                        }
                    }
                    ToolMode.SMUDGE -> {
                        currentDrawPath.lineTo(
                            modelX,
                            modelY
                        )
                        performSmudge(
                            activeLayer.bitmap,
                            lastSmudgePx,
                            lastSmudgePy,
                            modelX.toInt(),
                            modelY.toInt()
                        )
                        lastSmudgePx =
                            modelX.toInt()
                        lastSmudgePy =
                            modelY.toInt()
                        invalidate()
                    }
                    ToolMode.PIPPETE -> {
                        val px =
                            modelX.toInt()
                        val py =
                            modelY.toInt()
                        if (px in 0 until
                            activeLayer.bitmap.width &&
                            py in 0 until
                            activeLayer.bitmap.height
                        ) {
                            val color =
                                activeLayer.bitmap.getPixel(
                                    px,
                                    py
                                )
                            brushColor = color
                            colorPickedListener
                                ?.onColorPicked(color)
                        }
                    }
                    ToolMode.LINE,
                    ToolMode.RECTANGLE,
                    ToolMode.CIRCLE,
                    ToolMode.ARROW,
                    ToolMode.GRADIENT,
                    ToolMode.SELECT_RECT -> {
                        invalidate()
                    }
                    ToolMode.LASSO -> {
                        currentDrawPath.lineTo(
                            modelX,
                            modelY
                        )
                        invalidate()
                    }
                    ToolMode.AIRBRUSH,
                    ToolMode.BLUR -> {
                        currentDrawPath.lineTo(
                            modelX,
                            modelY
                        )
                        applyAirbrushEffect(
                            layerCanvas,
                            modelX,
                            modelY
                        )
                        invalidate()
                    }
                    ToolMode.BRUSH,
                    ToolMode.ERASER -> {
                        currentDrawPath.lineTo(
                            modelX,
                            modelY
                        )
                        invalidate()
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP -> {
                isTouchingCanvas = false
                currentX = modelX
                currentY = modelY
                isDrawingShape = false
                activeHandle = -1
                val currentLayerId =
                    activeLayer.id
                when (currentTool) {
                    ToolMode.FILL -> {
                        performFloodFillScanline(
                            activeLayer.bitmap,
                            modelX.toInt(),
                            modelY.toInt(),
                            brushColor
                        )
                        saveStrokeToHistory(
                            currentLayerId,
                            ToolMode.FILL
                        )
                        invalidate()
                    }
                    ToolMode.LINE,
                    ToolMode.RECTANGLE,
                    ToolMode.CIRCLE,
                    ToolMode.ARROW -> {
                        setupPaintForCurrentTool()
                        withSelectionClip(layerCanvas) {
                            drawSymmetric(layerCanvas) { c ->
                                drawShape(
                                    c,
                                    currentTool,
                                    startX,
                                    startY,
                                    currentX,
                                    currentY,
                                    drawPaint
                                )
                            }
                        }
                        saveStrokeToHistory(
                            currentLayerId,
                            currentTool,
                            startX,
                            startY,
                            currentX,
                            currentY,
                            null
                        )
                        invalidate()
                    }
                    ToolMode.GRADIENT -> {
                        val shader =
                            if (gradientType ==
                                GradientType.LINEAR
                            ) {
                                LinearGradient(
                                    startX,
                                    startY,
                                    currentX,
                                    currentY,
                                    brushColor,
                                    gradientEndColor,
                                    Shader.TileMode.CLAMP
                                )
                            } else {
                                val radius =
                                    Math.hypot(
                                        (
                                            currentX -
                                                startX
                                            ).toDouble(),
                                        (
                                            currentY -
                                                startY
                                            ).toDouble()
                                    ).toFloat()
                                        .coerceAtLeast(1f)
                                RadialGradient(
                                    startX,
                                    startY,
                                    radius,
                                    brushColor,
                                    gradientEndColor,
                                    Shader.TileMode.CLAMP
                                )
                            }
                        val gPaint = Paint(
                            Paint.ANTI_ALIAS_FLAG
                        ).apply {
                            this.shader = shader
                            style = Paint.Style.FILL
                        }
                        withSelectionClip(layerCanvas) {
                            layerCanvas.drawRect(
                                0f,
                                0f,
                                canvasWidth.toFloat(),
                                canvasHeight.toFloat(),
                                gPaint
                            )
                        }
                        saveStrokeToHistory(
                            currentLayerId,
                            currentTool
                        )
                        invalidate()
                    }
                    ToolMode.LASSO -> {
                        currentDrawPath.lineTo(
                            modelX,
                            modelY
                        )
                        currentDrawPath.close()
                        val newSubPath =
                            Path(currentDrawPath)
                        addPathToSelection(
                            newSubPath
                        )
                        currentDrawPath.reset()
                        isSelectionActive = true
                        invalidate()
                    }
                    ToolMode.SELECT_RECT -> {
                        val newSubPath =
                            Path().apply {
                                addRect(
                                    Math.min(
                                        startX,
                                        currentX
                                    ),
                                    Math.min(
                                        startY,
                                        currentY
                                    ),
                                    Math.max(
                                        startX,
                                        currentX
                                    ),
                                    Math.max(
                                        startY,
                                        currentY
                                    ),
                                    Path.Direction.CW
                                )
                            }
                        addPathToSelection(
                            newSubPath
                        )
                        isSelectionActive = true
                        invalidate()
                    }
                    ToolMode.AIRBRUSH -> {
                        saveStrokeToHistory(
                            currentLayerId,
                            currentTool
                        )
                        currentDrawPath.reset()
                        invalidate()
                    }
                    ToolMode.BLUR,
                    ToolMode.BRUSH,
                    ToolMode.ERASER -> {
                        withSelectionClip(layerCanvas) {
                            drawSymmetric(layerCanvas) { c ->
                                c.drawPath(
                                    currentDrawPath,
                                    drawPaint
                                )
                            }
                        }
                        saveStrokeToHistory(
                            currentLayerId,
                            currentTool,
                            path = currentDrawPath
                        )
                        currentDrawPath.reset()
                        invalidate()
                    }
                    ToolMode.SMUDGE -> {
                        drawPaint.maskFilter = null
                        drawPaint.xfermode = null
                        saveStrokeToHistory(
                            currentLayerId,
                            currentTool
                        )
                        currentDrawPath.reset()
                        lastSmudgePx = -1
                        lastSmudgePy = -1
                        invalidate()
                    }
                    else -> {}
                }
            }
        }
        return true
    }
    private fun addPathToSelection(
        newPath: Path
    ) {
        if (selectionMode ==
            SelectionMode.SINGLE_SELECT
        ) {
            selectedPathsQueue.clear()
            selectionPath.reset()
            selectedPathsQueue.addLast(
                newPath
            )
            selectionPath.addPath(
                newPath
            )
        } else {
            selectedPathsQueue.addLast(
                newPath
            )
            selectionPath.addPath(
                newPath
            )
        }
    }
    private inline fun withSelectionClip(
        canvas: Canvas,
        block: () -> Unit
    ) {
        if (isSelectionActive) {
            canvas.save()
            canvas.clipPath(selectionPath)
            block()
            canvas.restore()
        } else {
            block()
        }
    }
    private fun setupPaintForCurrentTool() {
        if (currentTool == ToolMode.ERASER ||
            brushColor == Color.TRANSPARENT
        ) {
            drawPaint.xfermode =
                PorterDuffXfermode(
                    PorterDuff.Mode.CLEAR
                )
            drawPaint.strokeWidth =
                if (currentTool ==
                    ToolMode.ERASER
                ) {
                    eraserSize
                } else {
                    brushSize
                }
            drawPaint.maskFilter = null
        } else {
            drawPaint.xfermode = null
            drawPaint.color = brushColor
            drawPaint.strokeWidth = brushSize
            if (currentTool ==
                ToolMode.BLUR
            ) {
                drawPaint.maskFilter =
                    BlurMaskFilter(
                        Math.max(
                            brushSize,
                            1f
                        ),
                        BlurMaskFilter.Blur.NORMAL
                    )
            } else {
                drawPaint.maskFilter = null
            }
        }
    }
    private fun drawShape(
        canvas: Canvas,
        tool: ToolMode,
        sx: Float,
        sy: Float,
        cx: Float,
        cy: Float,
        paint: Paint
    ) {
        when (tool) {
            ToolMode.LINE -> {
                canvas.drawLine(
                    sx,
                    sy,
                    cx,
                    cy,
                    paint
                )
            }
            ToolMode.RECTANGLE -> {
                paint.style =
                    Paint.Style.STROKE
                canvas.drawRect(
                    Math.min(sx, cx),
                    Math.min(sy, cy),
                    Math.max(sx, cx),
                    Math.max(sy, cy),
                    paint
                )
            }
            ToolMode.CIRCLE -> {
                paint.style =
                    Paint.Style.STROKE
                val radius =
                    Math.hypot(
                        (
                            cx - sx
                        ).toDouble(),
                        (
                            cy - sy
                        ).toDouble()
                    ).toFloat()
                canvas.drawCircle(
                    sx,
                    sy,
                    radius,
                    paint
                )
            }
            ToolMode.ARROW -> {
                canvas.drawLine(
                    sx,
                    sy,
                    cx,
                    cy,
                    paint
                )
                val angle =
                    Math.atan2(
                        (
                            cy - sy
                        ).toDouble(),
                        (
                            cx - sx
                        ).toDouble()
                    ).toFloat()
                val arrowSize =
                    paint.strokeWidth * 3
                val path = Path()
                path.moveTo(
                    cx,
                    cy
                )
                path.lineTo(
                    (
                        cx -
                            arrowSize *
                            Math.cos(
                                angle -
                                    Math.PI / 6
                            )
                        ).toFloat(),
                    (
                        cy -
                            arrowSize *
                            Math.sin(
                                angle -
                                    Math.PI / 6
                            )
                        ).toFloat()
                )
                path.lineTo(
                    (
                        cx -
                            arrowSize *
                            Math.cos(
                                angle +
                                    Math.PI / 6
                            )
                        ).toFloat(),
                    (
                        cy -
                            arrowSize *
                            Math.sin(
                                angle +
                                    Math.PI / 6
                            )
                        ).toFloat()
                )
                path.close()
                paint.style =
                    Paint.Style.FILL
                canvas.drawPath(
                    path,
                    paint
                )
                paint.style =
                    Paint.Style.STROKE
            }
            else -> {}
        }
    }
    private inline fun drawSymmetric(
        canvas: Canvas,
        drawBlock: (Canvas) -> Unit
    ) {
        drawBlock(canvas)
        if (symmetryMode ==
            SymmetryMode.NONE
        ) {
            return
        }
        canvas.save()
        when (symmetryMode) {
            SymmetryMode.HORIZONTAL -> {
                canvas.scale(
                    1f,
                    -1f,
                    canvasWidth / 2f,
                    canvasHeight / 2f
                )
                drawBlock(canvas)
            }
            SymmetryMode.VERTICAL -> {
                canvas.scale(
                    -1f,
                    1f,
                    canvasWidth / 2f,
                    canvasHeight / 2f
                )
                drawBlock(canvas)
            }
            SymmetryMode.RADIAL -> {
                val angle =
                    360f / symmetryRadialSectors
                for (i in 1 until symmetryRadialSectors) {
                    canvas.rotate(
                        angle,
                        canvasWidth / 2f,
                        canvasHeight / 2f
                    )
                    drawBlock(canvas)
                }
            }
            else -> {}
        }
        canvas.restore()
    }
    private fun applyAirbrushEffect(
        canvas: Canvas,
        x: Float,
        y: Float
    ) {
        if (currentTool ==
            ToolMode.AIRBRUSH
        ) {
            val radius = brushSize
            val p = Paint().apply {
                color = brushColor
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            withSelectionClip(canvas) {
                drawSymmetric(canvas) { c ->
                    for (i in 0..15) {
                        val dx = (
                            randomGen.nextGaussian() *
                                radius / 2
                            ).toFloat()
                        val dy = (
                            randomGen.nextGaussian() *
                                radius / 2
                            ).toFloat()
                        if (dx * dx +
                            dy * dy <=
                            radius * radius
                        ) {
                            c.drawCircle(
                                x + dx,
                                y + dy,
                                2f,
                                p
                            )
                        }
                    }
                }
            }
        }
    }
    private fun performSmudge(
        bmp: Bitmap,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int
    ) {
        if (bmp.isRecycled) {
            return
        }
        val radius =
            (brushSize / 2f)
                .toInt()
                .coerceAtLeast(1)
        if (x1 !in radius until
            bmp.width - radius ||
            y1 !in radius until
            bmp.height - radius ||
            x2 !in radius until
            bmp.width - radius ||
            y2 !in radius until
            bmp.height - radius
        ) {
            return
        }
        val patchSize =
            radius * 2
        if (!isValidCanvasSize(
                patchSize,
                patchSize
            )
        ) {
            return
        }
        val patch = try {
            Bitmap.createBitmap(
                bmp,
                x1 - radius,
                y1 - radius,
                patchSize,
                patchSize
            )
        } catch (e: Exception) {
            return
        } catch (e: OutOfMemoryError) {
            return
        }
        val canvas = Canvas(bmp)
        val p = Paint().apply {
            alpha =
                (
                    smudgeStrength * 255
                    ).toInt()
                    .coerceIn(0, 255)
            isAntiAlias = true
        }
        canvas.drawBitmap(
            patch,
            (x2 - radius).toFloat(),
            (y2 - radius).toFloat(),
            p
        )
        if (!patch.isRecycled) {
            patch.recycle()
        }
    }
    private fun rebuildVectorPath() {
        vectorPath.reset()
        if (vectorPoints.isEmpty()) {
            return
        }
        vectorPath.moveTo(
            vectorPoints[0].x,
            vectorPoints[0].y
        )
        for (i in 1 until vectorPoints.size) {
            vectorPath.lineTo(
                vectorPoints[i].x,
                vectorPoints[i].y
            )
        }
    }
    fun bakeVectorPathToLayer(
        close: Boolean = false
    ) {
        if (activeLayerIndex >=
            layersList.size ||
            vectorPoints.isEmpty()
        ) {
            return
        }
        if (close) {
            vectorPath.close()
        }
        val activeLayer =
            layersList[activeLayerIndex]
        val c =
            Canvas(activeLayer.bitmap)
        setupPaintForCurrentTool()
        withSelectionClip(c) {
            c.drawPath(
                vectorPath,
                drawPaint
            )
        }
        saveStrokeToHistory(
            activeLayer.id,
            ToolMode.BRUSH,
            path = vectorPath
        )
        vectorPoints.clear()
        vectorPath.reset()
        invalidate()
    }
    private fun initTransformForActiveLayer() {
        layerTransformMatrix.reset()
        transformBounds.set(
            0f,
            0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat()
        )
    }
    private fun get12HandlePoints(
        rect: RectF,
        hasTopRotationStem: Boolean = false
    ): Array<PointF> {
        val rotDist = 50f
        return arrayOf(
            PointF(
                rect.left,
                rect.top
            ),
            PointF(
                rect.centerX(),
                rect.top
            ),
            PointF(
                rect.right,
                rect.top
            ),
            PointF(
                rect.right,
                rect.centerY()
            ),
            PointF(
                rect.right,
                rect.bottom
            ),
            PointF(
                rect.centerX(),
                rect.bottom
            ),
            PointF(
                rect.left,
                rect.bottom
            ),
            PointF(
                rect.left,
                rect.centerY()
            ),
            PointF(
                rect.centerX(),
                rect.top - rotDist
            ),
            if (hasTopRotationStem) {
                PointF(
                    rect.centerX(),
                    rect.top - rotDist
                )
            } else {
                PointF(
                    rect.right + rotDist,
                    rect.top - rotDist
                )
            },
            if (hasTopRotationStem) {
                PointF(
                    rect.centerX(),
                    rect.top - rotDist
                )
            } else {
                PointF(
                    rect.right + rotDist,
                    rect.bottom + rotDist
                )
            },
            if (hasTopRotationStem) {
                PointF(
                    rect.centerX(),
                    rect.top - rotDist
                )
            } else {
                PointF(
                    rect.left - rotDist,
                    rect.bottom + rotDist
                )
            }
        )
    }
    private fun getInteractiveHandleAt(
        rect: RectF,
        matrix: Matrix?,
        x: Float,
        y: Float,
        isCropTool: Boolean = false,
        hasTopRotationStem: Boolean = false
    ): Int {
        val handleRadius = 55f
        val handles =
            get12HandlePoints(
                rect,
                hasTopRotationStem
            )
        val pts = FloatArray(24)
        for (i in 0 until 12) {
            pts[i * 2] =
                handles[i].x
            pts[i * 2 + 1] =
                handles[i].y
        }
        matrix?.mapPoints(pts)
        val maxCheck = when {
            isCropTool -> 8
            hasTopRotationStem -> 9
            else -> 12
        }
        for (i in 0 until maxCheck) {
            val hx = pts[i * 2]
            val hy = pts[i * 2 + 1]
            if (Math.hypot(
                    (x - hx).toDouble(),
                    (y - hy).toDouble()
                ) <= handleRadius
            ) {
                return i
            }
        }
        if (matrix != null) {
            val inv = Matrix()
            if (matrix.invert(inv)) {
                val localPoint =
                    floatArrayOf(
                        x,
                        y
                    )
                inv.mapPoints(
                    localPoint
                )
                if (rect.contains(
                        localPoint[0],
                        localPoint[1]
                    )
                ) {
                    return 12
                }
            }
        } else if (rect.contains(x, y)) {
            return 12
        }
        return -1
    }
    private fun updateCropRect(
        handle: Int,
        touchX: Float,
        touchY: Float,
        dx: Float,
        dy: Float
    ) {
        val minSize = 32f
        if (handle == 12) {
            val w = cropRect.width()
            val h = cropRect.height()
            var left =
                cropRect.left + dx
            var top =
                cropRect.top + dy
            if (w > canvasWidth ||
                h > canvasHeight
            ) {
                return
            }
            left = left.coerceIn(
                0f,
                canvasWidth - w
            )
            top = top.coerceIn(
                0f,
                canvasHeight - h
            )
            cropRect.set(
                left,
                top,
                left + w,
                top + h
            )
            return
        }
        if (handle < 0) {
            return
        }
        var left = cropRect.left
        var top = cropRect.top
        var right = cropRect.right
        var bottom = cropRect.bottom
        when (handle) {
            0 -> {
                left = touchX.coerceAtMost(
                    right - minSize
                )
                top = touchY.coerceAtMost(
                    bottom - minSize
                )
            }
            1 -> {
                top = touchY.coerceAtMost(
                    bottom - minSize
                )
            }
            2 -> {
                right = touchX.coerceAtLeast(
                    left + minSize
                )
                top = touchY.coerceAtMost(
                    bottom - minSize
                )
            }
            3 -> {
                right = touchX.coerceAtLeast(
                    left + minSize
                )
            }
            4 -> {
                right = touchX.coerceAtLeast(
                    left + minSize
                )
                bottom = touchY.coerceAtLeast(
                    top + minSize
                )
            }
            5 -> {
                bottom = touchY.coerceAtLeast(
                    top + minSize
                )
            }
            6 -> {
                left = touchX.coerceAtMost(
                    right - minSize
                )
                bottom = touchY.coerceAtLeast(
                    top + minSize
                )
            }
            7 -> {
                left = touchX.coerceAtMost(
                    right - minSize
                )
            }
        }
        left = left.coerceIn(
            0f,
            canvasWidth.toFloat()
        )
        top = top.coerceIn(
            0f,
            canvasHeight.toFloat()
        )
        right = right.coerceIn(
            0f,
            canvasWidth.toFloat()
        )
        bottom = bottom.coerceIn(
            0f,
            canvasHeight.toFloat()
        )
        if (right - left < minSize) {
            if (handle == 0 ||
                handle == 6 ||
                handle == 7
            ) {
                left =
                    (right - minSize)
                        .coerceAtLeast(0f)
            } else {
                right =
                    (left + minSize)
                        .coerceAtMost(
                            canvasWidth.toFloat()
                        )
            }
        }
        if (bottom - top < minSize) {
            if (handle == 0 ||
                handle == 1 ||
                handle == 2
            ) {
                top =
                    (bottom - minSize)
                        .coerceAtLeast(0f)
            } else {
                bottom =
                    (top + minSize)
                        .coerceAtMost(
                            canvasHeight.toFloat()
                        )
            }
        }
        cropRect.set(
            left,
            top,
            right,
            bottom
        )
        cropRect.left =
            cropRect.left.coerceIn(
                0f,
                canvasWidth.toFloat()
            )
        cropRect.top =
            cropRect.top.coerceIn(
                0f,
                canvasHeight.toFloat()
            )
        cropRect.right =
            cropRect.right.coerceIn(
                0f,
                canvasWidth.toFloat()
            )
        cropRect.bottom =
            cropRect.bottom.coerceIn(
                0f,
                canvasHeight.toFloat()
            )
    }
    private fun updateInteractiveTransform(
        handle: Int,
        rect: RectF,
        matrix: Matrix,
        touchX: Float,
        touchY: Float,
        dx: Float,
        dy: Float,
        isCrop: Boolean = false,
        hasTopRotationStem: Boolean = false
    ) {
        if (handle == 12) {
            matrix.postTranslate(
                dx,
                dy
            )
            return
        }
        if (handle < 0) {
            return
        }
        val centerPts = floatArrayOf(
            rect.centerX(),
            rect.centerY()
        )
        matrix.mapPoints(
            centerPts
        )
        val cx = centerPts[0]
        val cy = centerPts[1]
        if (handle == 8 &&
            !isCrop
        ) {
            val currentAngle =
                Math.toDegrees(
                    Math.atan2(
                        (
                            touchY - cy
                            ).toDouble(),
                        (
                            touchX - cx
                            ).toDouble()
                    )
                ).toFloat()
            val prevAngle =
                Math.toDegrees(
                    Math.atan2(
                        (
                            touchY -
                                dy -
                                cy
                            ).toDouble(),
                        (
                            touchX -
                                dx -
                                cx
                            ).toDouble()
                    )
                ).toFloat()
            var deltaAngle =
                currentAngle - prevAngle
            if (deltaAngle > 180f) {
                deltaAngle -= 360f
            }
            if (deltaAngle < -180f) {
                deltaAngle += 360f
            }
            matrix.postRotate(
                deltaAngle,
                cx,
                cy
            )
            return
        }
        val inv = Matrix()
        if (!matrix.invert(inv)) {
            return
        }
        val pts = floatArrayOf(
            touchX,
            touchY,
            touchX - dx,
            touchY - dy
        )
        inv.mapPoints(pts)
        val localX = pts[0]
        val localY = pts[1]
        val localPrevX = pts[2]
        val localPrevY = pts[3]
        val localDx =
            localX - localPrevX
        val localDy =
            localY - localPrevY
        val boundsW =
            rect.width().coerceAtLeast(1f)
        val boundsH =
            rect.height().coerceAtLeast(1f)
        var scaleX = 1f
        var scaleY = 1f
        var pivotX = rect.centerX()
        var pivotY = rect.centerY()
        when (handle) {
            0 -> {
                pivotX = rect.right
                pivotY = rect.bottom
                scaleX =
                    (boundsW - localDx) /
                        boundsW
                scaleY =
                    (boundsH - localDy) /
                        boundsH
            }
            1 -> {
                pivotY = rect.bottom
                scaleY =
                    (boundsH - localDy) /
                        boundsH
            }
            2 -> {
                pivotX = rect.left
                pivotY = rect.bottom
                scaleX =
                    (boundsW + localDx) /
                        boundsW
                scaleY =
                    (boundsH - localDy) /
                        boundsH
            }
            3 -> {
                pivotX = rect.left
                scaleX =
                    (boundsW + localDx) /
                        boundsW
            }
            4 -> {
                pivotX = rect.left
                pivotY = rect.top
                scaleX =
                    (boundsW + localDx) /
                        boundsW
                scaleY =
                    (boundsH + localDy) /
                        boundsH
            }
            5 -> {
                pivotY = rect.top
                scaleY =
                    (boundsH + localDy) /
                        boundsH
            }
            6 -> {
                pivotX = rect.right
                pivotY = rect.top
                scaleX =
                    (boundsW - localDx) /
                        boundsW
                scaleY =
                    (boundsH + localDy) /
                        boundsH
            }
            7 -> {
                pivotX = rect.right
                scaleX =
                    (boundsW - localDx) /
                        boundsW
            }
        }
        scaleX = scaleX.coerceIn(
            0.02f,
            100f
        )
        scaleY = scaleY.coerceIn(
            0.02f,
            100f
        )
        val pivotPts = floatArrayOf(
            pivotX,
            pivotY
        )
        matrix.mapPoints(
            pivotPts
        )
        matrix.preScale(
            scaleX,
            scaleY,
            pivotX,
            pivotY
        )
    }
    fun applyTransformToActiveLayer() {
        if (activeLayerIndex >=
            layersList.size ||
            layerTransformMatrix.isIdentity
        ) {
            return
        }
        val activeLayer =
            layersList[activeLayerIndex]
        val transformedBmp =
            createSafeBitmap(
                canvasWidth,
                canvasHeight
            ) ?: return
        val c =
            Canvas(transformedBmp)
        c.drawBitmap(
            activeLayer.bitmap,
            layerTransformMatrix,
            layerBlendPaint
        )
        if (!activeLayer.bitmap.isRecycled) {
            activeLayer.bitmap.recycle()
        }
        activeLayer.bitmap =
            transformedBmp
        undoActions.add(
            DrawAction.TransformLayer(
                activeLayer.id,
                Matrix(layerTransformMatrix)
            )
        )
        layerTransformMatrix.reset()
        redoActions.clear()
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
        invalidate()
    }
    fun applyCropCanvas() {
        var targetW =
            cropRect.width()
                .toInt()
                .coerceAtLeast(1)
        var targetH =
            cropRect.height()
                .toInt()
                .coerceAtLeast(1)
        targetW =
            targetW.coerceAtMost(
                MAX_CANVAS_SIZE
            )
        targetH =
            targetH.coerceAtMost(
                MAX_CANVAS_SIZE
            )
        if (!isValidCanvasSize(
                targetW,
                targetH
            )
        ) {
            return
        }
        val offsetX =
            -cropRect.left
        val offsetY =
            -cropRect.top
        val snapshots =
            HashMap<String, Bitmap>()
        val newBitmaps =
            HashMap<String, Bitmap>()
        for (layer in layersList) {
            val srcBmp =
                layer.bitmap
            val snapshot =
                copyBitmapSafely(srcBmp)
                    ?: run {
                        newBitmaps.values.forEach {
                            if (!it.isRecycled) {
                                it.recycle()
                            }
                        }
                        snapshots.values.forEach {
                            if (!it.isRecycled) {
                                it.recycle()
                            }
                        }
                        return
                    }
            val newBmp =
                createSafeBitmap(
                    targetW,
                    targetH
                ) ?: run {
                    snapshot.recycle()
                    newBitmaps.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    snapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    return
                }
            val c =
                Canvas(newBmp)
            c.rotate(
                -cropRotation,
                targetW / 2f,
                targetH / 2f
            )
            c.drawBitmap(
                srcBmp,
                offsetX,
                offsetY,
                null
            )
            snapshots[layer.id] =
                snapshot
            newBitmaps[layer.id] =
                newBmp
        }
        layersList.forEach { layer ->
            val newBmp =
                newBitmaps[layer.id]
            if (newBmp != null) {
                if (!layer.bitmap.isRecycled) {
                    layer.bitmap.recycle()
                }
                layer.bitmap = newBmp
            }
        }
        val afterSnapshots = HashMap<String, Bitmap>()
        for (layer in layersList) {
            val sourceBitmap: Bitmap = newBitmaps[layer.id] ?: run {
                newBitmaps.values.forEach {
                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }
                snapshots.values.forEach {
                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }
                afterSnapshots.values.forEach {
                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }
                return
            }
            val snapshot = copyBitmapSafely(sourceBitmap)
                ?: run {
                    newBitmaps.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    snapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    afterSnapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    return
                }
            afterSnapshots[layer.id] = snapshot
        }
        val beforeWidth = canvasWidth
        val beforeHeight = canvasHeight
        undoActions.add(
            DrawAction.CanvasResize(
                beforeWidth,
                beforeHeight,
                targetW,
                targetH,
                snapshots,
                afterSnapshots
            )
        )
        this.canvasWidth = targetW
        this.canvasHeight = targetH
        cropRect.set(
            0f,
            0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat()
        )
        cropRotation = 0f
        resetStampTransform()
        layerTransformMatrix.reset()
        centerCanvasInView()
        redoActions.clear()
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
        invalidate()
    }
    fun applyGaussianBlurToActiveLayer(
        radius: Float
    ) {
        if (activeLayerIndex >=
            layersList.size ||
            radius <= 0f
        ) {
            return
        }
        val activeLayer =
            layersList[activeLayerIndex]
        val src =
            activeLayer.bitmap
        val out =
            createSafeBitmap(
                src.width,
                src.height,
                src.config
            ) ?: return
        val canvas =
            Canvas(out)
        val paint =
            Paint().apply {
                isAntiAlias = true
                maskFilter =
                    BlurMaskFilter(
                        radius,
                        BlurMaskFilter.Blur.NORMAL
                    )
            }
        withSelectionClip(canvas) {
            canvas.drawBitmap(
                src,
                0f,
                0f,
                paint
            )
        }
        if (!activeLayer.bitmap.isRecycled) {
            activeLayer.bitmap.recycle()
        }
        activeLayer.bitmap = out
        val historyBitmap =
            copyBitmapSafely(out)
        if (historyBitmap != null) {
            undoActions.add(
                DrawAction.ApplyFilter(
                    activeLayer.id,
                    historyBitmap
                )
            )
        }
        redoActions.clear()
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
        invalidate()
    }
    fun applyColorAdjustments(
        brightness: Float,
        contrast: Float,
        saturation: Float
    ) {
        if (activeLayerIndex >=
            layersList.size
        ) {
            return
        }
        val activeLayer =
            layersList[activeLayerIndex]
        val cm = ColorMatrix()
        val matrix = ColorMatrix()
        matrix.setSaturation(
            saturation
        )
        cm.postConcat(matrix)
        val scale = contrast
        val translate = (
            -0.5f * scale +
                0.5f +
                brightness
            ) * 255f
        val contrastMatrix =
            ColorMatrix(
                floatArrayOf(
                    scale,
                    0f,
                    0f,
                    0f,
                    translate,
                    0f,
                    scale,
                    0f,
                    0f,
                    translate,
                    0f,
                    0f,
                    scale,
                    0f,
                    translate,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f
                )
            )
        cm.postConcat(
            contrastMatrix
        )
        val resultBmp =
            createSafeBitmap(
                canvasWidth,
                canvasHeight
            ) ?: return
        val c =
            Canvas(resultBmp)
        val p =
            Paint().apply {
                colorFilter =
                    ColorMatrixColorFilter(cm)
            }
        withSelectionClip(c) {
            c.drawBitmap(
                activeLayer.bitmap,
                0f,
                0f,
                p
            )
        }
        if (!activeLayer.bitmap.isRecycled) {
            activeLayer.bitmap.recycle()
        }
        activeLayer.bitmap =
            resultBmp
        val historyBitmap =
            copyBitmapSafely(resultBmp)
        if (historyBitmap != null) {
            undoActions.add(
                DrawAction.ApplyFilter(
                    activeLayer.id,
                    historyBitmap
                )
            )
        }
        redoActions.clear()
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
        invalidate()
    }
    fun copySelection() {
        if (!isSelectionActive ||
            activeLayerIndex >=
            layersList.size
        ) {
            return
        }
        val activeLayer =
            layersList[activeLayerIndex]
        val rectF = RectF()
        selectionPath.computeBounds(
            rectF,
            true
        )
        if (rectF.width() <= 0 ||
            rectF.height() <= 0
        ) {
            return
        }
        val width =
            rectF.width()
                .toInt()
                .coerceAtMost(
                    MAX_CANVAS_SIZE
                )
        val height =
            rectF.height()
                .toInt()
                .coerceAtMost(
                    MAX_CANVAS_SIZE
                )
        if (!isValidCanvasSize(
                width,
                height
            )
        ) {
            return
        }
        val cropped =
            createSafeBitmap(
                width,
                height
            ) ?: return
        val c =
            Canvas(cropped)
        c.translate(
            -rectF.left,
            -rectF.top
        )
        c.clipPath(
            selectionPath
        )
        c.drawBitmap(
            activeLayer.bitmap,
            0f,
            0f,
            null
        )
        clipboardBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        clipboardBitmap =
            cropped
    }
    fun cutSelection() {
        copySelection()
        deleteSelectionContent()
    }
    fun pasteSelection() {
        val clipboard =
            clipboardBitmap
        if (clipboard == null ||
            clipboard.isRecycled ||
            activeLayerIndex >=
            layersList.size
        ) {
            return
        }
        val activeLayer =
            layersList[activeLayerIndex]
        val c =
            Canvas(activeLayer.bitmap)
        val cx =
            (
                canvasWidth -
                    clipboard.width
                ) / 2f
        val cy =
            (
                canvasHeight -
                    clipboard.height
                ) / 2f
        withSelectionClip(c) {
            c.drawBitmap(
                clipboard,
                cx,
                cy,
                null
            )
        }
        saveStrokeToHistory(
            activeLayer.id,
            ToolMode.STAMP_IMAGE
        )
        invalidate()
    }
    fun deleteSelectionContent() {
        if (!isSelectionActive ||
            activeLayerIndex >=
            layersList.size
        ) {
            return
        }
        val activeLayer =
            layersList[activeLayerIndex]
        val c =
            Canvas(activeLayer.bitmap)
        val p =
            Paint().apply {
                xfermode =
                    PorterDuffXfermode(
                        PorterDuff.Mode.CLEAR
                    )
            }
        c.drawPath(
            selectionPath,
            p
        )
        saveStrokeToHistory(
            activeLayer.id,
            ToolMode.ERASER
        )
        invalidate()
    }
    fun clearSelection() {
        selectionPath.reset()
        selectedPathsQueue.clear()
        isSelectionActive = false
        invalidate()
    }
    fun invertSelection() {
        if (!isSelectionActive) {
            return
        }
        val fullPath =
            Path().apply {
                addRect(
                    0f,
                    0f,
                    canvasWidth.toFloat(),
                    canvasHeight.toFloat(),
                    Path.Direction.CW
                )
            }
        fullPath.op(
            selectionPath,
            Path.Op.DIFFERENCE
        )
        selectionPath =
            fullPath
        selectedPathsQueue.clear()
        selectedPathsQueue.add(
            selectionPath
        )
        invalidate()
    }
    fun fillSelection() {
        if (!isSelectionActive ||
            activeLayerIndex >=
            layersList.size
        ) {
            return
        }
        val activeLayer =
            layersList[activeLayerIndex]
        val c =
            Canvas(activeLayer.bitmap)
        val p =
            Paint().apply {
                color = brushColor
                style = Paint.Style.FILL
            }
        c.drawPath(
            selectionPath,
            p
        )
        saveStrokeToHistory(
            activeLayer.id,
            ToolMode.FILL
        )
        invalidate()
    }
    private fun performFloodFillScanline(
        bmp: Bitmap,
        startX: Int,
        startY: Int,
        targetColor: Int
    ) {
        if (bmp.isRecycled ||
            startX !in 0 until bmp.width ||
            startY !in 0 until bmp.height
        ) {
            return
        }
        val srcColor =
            bmp.getPixel(
                startX,
                startY
            )
        if (srcColor == targetColor) {
            return
        }
        val w = bmp.width
        val h = bmp.height
        val pixelCount =
            w.toLong() * h.toLong()
        if (pixelCount <= 0 ||
            pixelCount > MAX_BITMAP_PIXELS
        ) {
            return
        }
        val pixels =
            try {
                IntArray(
                    pixelCount.toInt()
                )
            } catch (e: OutOfMemoryError) {
                return
            }
        bmp.getPixels(
            pixels,
            0,
            w,
            0,
            0,
            w,
            h
        )
        val maxSegments =
            (w.toLong() * h.toLong())
                .coerceAtMost(
                    4_000_000L
                )
                .toInt()
        val stackX =
            try {
                IntArray(
                    maxSegments
                )
            } catch (e: OutOfMemoryError) {
                return
            }
        val stackY =
            try {
                IntArray(
                    maxSegments
                )
            } catch (e: OutOfMemoryError) {
                return
            }
        var top = 0
        stackX[top] = startX
        stackY[top] = startY
        top++
        while (top > 0) {
            top--
            val cx =
                stackX[top]
            val cy =
                stackY[top]
            var left = cx
            while (
                left >= 0 &&
                colorMatch(
                    pixels[
                        cy * w + left
                    ],
                    srcColor,
                    fillTolerance
                )
            ) {
                left--
            }
            left++
            var right = cx
            while (
                right < w &&
                colorMatch(
                    pixels[
                        cy * w + right
                    ],
                    srcColor,
                    fillTolerance
                )
            ) {
                right++
            }
            right--
            for (x in left..right) {
                pixels[
                    cy * w + x
                ] = targetColor
            }
            if (top < maxSegments) {
                top = checkRowNeighbors(
                    pixels,
                    w,
                    h,
                    left,
                    right,
                    cy - 1,
                    srcColor,
                    targetColor,
                    stackX,
                    stackY,
                    top
                )
            }
            if (top < maxSegments) {
                top = checkRowNeighbors(
                    pixels,
                    w,
                    h,
                    left,
                    right,
                    cy + 1,
                    srcColor,
                    targetColor,
                    stackX,
                    stackY,
                    top
                )
            }
        }
        bmp.setPixels(
            pixels,
            0,
            w,
            0,
            0,
            w,
            h
        )
    }
    private fun checkRowNeighbors(
        pixels: IntArray,
        w: Int,
        h: Int,
        left: Int,
        right: Int,
        ny: Int,
        srcColor: Int,
        targetColor: Int,
        stackX: IntArray,
        stackY: IntArray,
        currentTop: Int
    ): Int {
        if (ny !in 0 until h) {
            return currentTop
        }
        var inSegment = false
        var top = currentTop
        for (x in left..right) {
            val idx =
                ny * w + x
            val isMatch =
                colorMatch(
                    pixels[idx],
                    srcColor,
                    fillTolerance
                ) &&
                    pixels[idx] != targetColor
            if (isMatch) {
                if (!inSegment) {
                    if (top >=
                        stackX.size
                    ) {
                        return top
                    }
                    stackX[top] = x
                    stackY[top] = ny
                    top++
                    inSegment = true
                }
            } else {
                inSegment = false
            }
        }
        return top
    }
    private fun colorMatch(
        c1: Int,
        c2: Int,
        tol: Int
    ): Boolean {
        if (c1 == c2) {
            return true
        }
        if ((c1 shr 24 and 0xFF) == 0 &&
            (c2 shr 24 and 0xFF) == 0
        ) {
            return true
        }
        val r1 =
            (c1 shr 16) and 0xFF
        val g1 =
            (c1 shr 8) and 0xFF
        val b1 =
            c1 and 0xFF
        val a1 =
            (c1 shr 24) and 0xFF
        val r2 =
            (c2 shr 16) and 0xFF
        val g2 =
            (c2 shr 8) and 0xFF
        val b2 =
            c2 and 0xFF
        val a2 =
            (c2 shr 24) and 0xFF
        if (Math.abs(
                a1 - a2
            ) > tol
        ) {
            return false
        }
        return Math.abs(
            r1 - r2
        ) <= tol &&
            Math.abs(
                g1 - g2
            ) <= tol &&
            Math.abs(
                b1 - b2
            ) <= tol
    }
    fun historyLogAddLayer(
        layerId: String,
        name: String
    ) {
        undoActions.add(
            DrawAction.AddLayer(
                layerId,
                name
            )
        )
        redoActions.clear()
        notifyHistoryListener()
    }
    fun historyLogDeleteLayer(
        layerId: String,
        name: String,
        currentBitmap: Bitmap
    ) {
        val snapshot =
            copyBitmapSafely(
                currentBitmap
            ) ?: return
        undoActions.add(
            DrawAction.DeleteLayer(
                layerId,
                name,
                snapshot
            )
        )
        redoActions.clear()
        notifyHistoryListener()
    }
    private fun saveStrokeToHistory(
        layerId: String,
        tool: ToolMode,
        sx: Float = 0f,
        sy: Float = 0f,
        cx: Float = 0f,
        cy: Float = 0f,
        path: Path? = null
    ) {
        if (activeLayerIndex !in layersList.indices) {
            return
        }
        val layer = layersList[activeLayerIndex]
        val snapshot = copyBitmapSafely(layer.bitmap)
            ?: return
        undoActions.add(
            DrawAction.BaseImage(
                layerId,
                snapshot
            )
        )
        redoActions.clear()
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
    }
    fun performUndo() {
        if (undoActions.size > 0) {
            val lastAction =
                undoActions.last()
            if (lastAction is
                DrawAction.BaseImage &&
                undoActions.size == 1
            ) {
                return
            }
            redoActions.add(
                undoActions.removeAt(
                    undoActions.size - 1
                )
            )
            rebuildCanvasFromActions()
        }
    }
    fun performRedo() {
        if (redoActions.isNotEmpty()) {
            undoActions.add(
                redoActions.removeAt(
                    redoActions.size - 1
                )
            )
            rebuildCanvasFromActions()
        }
    }
    private fun rebuildCanvasFromActions() {
        if (layersList.isEmpty()) {
            return
        }
        val baseImage = undoActions
            .filterIsInstance<DrawAction.BaseImage>()
            .firstOrNull()
            ?: return
        val initialWidth = baseImage.bitmap.width
        val initialHeight = baseImage.bitmap.height
        if (!isValidCanvasSize(
                initialWidth,
                initialHeight
            )
        ) {
            return
        }
        canvasWidth = initialWidth
        canvasHeight = initialHeight
        val preservedActiveLayerId =
            layersList.getOrNull(activeLayerIndex)?.id
        layersList.forEach {
            if (!it.bitmap.isRecycled) {
                it.bitmap.recycle()
            }
        }
        layersList.clear()
        val defaultBmp =
            createSafeBitmap(
                canvasWidth,
                canvasHeight
            ) ?: return
        layersList.add(
            CanvasLayer(
                "layer_0",
                "Layer 1",
                defaultBmp
            )
        )
        activeLayerIndex = 0
        val p = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        undoActions.forEach { action ->
            when (action) {
                is DrawAction.AddLayer -> {
                    val newBmp =
                        createSafeBitmap(
                            canvasWidth,
                            canvasHeight
                        ) ?: return@forEach
                    layersList.add(
                        CanvasLayer(
                            action.layerId,
                            action.name,
                            newBmp
                        )
                    )
                }
                is DrawAction.DeleteLayer -> {
                    layersList.removeAll {
                        it.id ==
                            action.layerId
                    }
                }
                is DrawAction.TransformLayer -> {
                    val layer =
                        layersList.find {
                            it.id ==
                                action.layerId
                        }
                    layer?.let {
                        val tb =
                            createSafeBitmap(
                                canvasWidth,
                                canvasHeight
                            ) ?: return@let
                        Canvas(tb).drawBitmap(
                            it.bitmap,
                            action.matrix,
                            layerBlendPaint
                        )
                        if (!it.bitmap.isRecycled) {
                            it.bitmap.recycle()
                        }
                        it.bitmap = tb
                    }
                }
                is DrawAction.ApplyFilter -> {
                    val layer =
                        layersList.find {
                            it.id ==
                                action.layerId
                        }
                    layer?.let {
                        val restored =
                            copyBitmapSafely(
                                action.resultBitmap
                            ) ?: return@let
                        if (!it.bitmap.isRecycled) {
                            it.bitmap.recycle()
                        }
                        it.bitmap = restored
                    }
                }
                is DrawAction.CanvasResize -> {
                    if (isValidCanvasSize(
                            action.afterWidth,
                            action.afterHeight
                        )
                    ) {
                        this.canvasWidth = action.afterWidth
                        this.canvasHeight = action.afterHeight
                        action.afterSnapshots.forEach {
                                (id, bmp) ->
                            val layer = layersList.find {
                                it.id == id
                            }
                            layer?.let {
                                val restored =
                                    copyBitmapSafely(bmp)
                                if (restored != null) {
                                    if (!it.bitmap.isRecycled) {
                                        it.bitmap.recycle()
                                    }
                                    it.bitmap = restored
                                }
                            }
                        }
                    }
                }
                is DrawAction.BaseImage,
                is DrawAction.Line,
                is DrawAction.Fill,
                is DrawAction.TextStamp,
                is DrawAction.ImageStamp,
                is DrawAction.DrawShape -> {
                    val targetLayerId =
                        when (action) {
                            is DrawAction.BaseImage ->
                                action.layerId
                            is DrawAction.Line ->
                                action.layerId
                            is DrawAction.Fill ->
                                action.layerId
                            is DrawAction.TextStamp ->
                                action.layerId
                            is DrawAction.ImageStamp ->
                                action.layerId
                            is DrawAction.DrawShape ->
                                action.layerId
                            else ->
                                "layer_0"
                        }
                    var targetLayer =
                        layersList.find {
                            it.id ==
                                targetLayerId
                        }
                    if (targetLayer == null &&
                        action is DrawAction.BaseImage
                    ) {
                        val restoredBmp =
                            createSafeBitmap(
                                canvasWidth,
                                canvasHeight
                            ) ?: return@forEach
                        targetLayer =
                            CanvasLayer(
                                targetLayerId,
                                "Restored Layer",
                                restoredBmp
                            )
                        layersList.add(
                            targetLayer
                        )
                    }
                    targetLayer?.let { layer ->
                        val canvas =
                            Canvas(
                                layer.bitmap
                            )
                        when (action) {
                            is DrawAction.BaseImage -> {
                                val restored = copyBitmapSafely(action.bitmap)
                                    ?: return@let
                                if (!layer.bitmap.isRecycled) {
                                    layer.bitmap.recycle()
                                }
                                layer.bitmap = restored
                            }
                            is DrawAction.Line -> {
                                p.color =
                                    action.color
                                p.strokeWidth =
                                    action.width
                                p.xfermode =
                                    if (action.isEraser) {
                                        PorterDuffXfermode(
                                            PorterDuff.Mode.CLEAR
                                        )
                                    } else {
                                        null
                                    }
                                canvas.drawPath(
                                    action.path,
                                    p
                                )
                            }
                            is DrawAction.Fill -> {
                                performFloodFillScanline(
                                    layer.bitmap,
                                    action.x,
                                    action.y,
                                    action.color
                                )
                            }
                            is DrawAction.TextStamp -> {
                                val tPaint =
                                    Paint().apply {
                                        color =
                                            action.color
                                        textSize =
                                            action.size
                                        typeface =
                                            action.typeface
                                        isAntiAlias =
                                            true
                                    }
                                canvas.save()
                                canvas.rotate(
                                    action.rotation,
                                    action.x,
                                    action.y
                                )
                                val lines =
                                    action.text.split(
                                        "\n"
                                    )
                                var currentY =
                                    action.y
                                lines.forEach { line ->
                                    canvas.drawText(
                                        line,
                                        action.x,
                                        currentY,
                                        tPaint
                                    )
                                    currentY +=
                                        tPaint.fontSpacing
                                }
                                canvas.restore()
                            }
                            is DrawAction.ImageStamp -> {
                                val matrix =
                                    Matrix().apply {
                                        postScale(
                                            action.scaleX,
                                            action.scaleY
                                        )
                                        postRotate(
                                            action.rotation
                                        )
                                        postTranslate(
                                            action.x,
                                            action.y
                                        )
                                    }
                                canvas.save()
                                canvas.concat(
                                    matrix
                                )
                                canvas.drawBitmap(
                                    action.bitmap,
                                    -action.bitmap.width / 2f,
                                    -action.bitmap.height / 2f,
                                    Paint(
                                        Paint.ANTI_ALIAS_FLAG
                                    )
                                )
                                canvas.restore()
                            }
                            is DrawAction.DrawShape -> {
                                p.color =
                                    action.color
                                p.strokeWidth =
                                    action.width
                                p.xfermode = null
                                drawShape(
                                    canvas,
                                    action.tool,
                                    action.sx,
                                    action.sy,
                                    action.cx,
                                    action.cy,
                                    p
                                )
                            }
                            else -> {}
                        }
                    }
                }
                is DrawAction.LayerStateChange -> {
                restoreLayerState(action.after)
                }
            }
        }
        val preservedIndex =
            preservedActiveLayerId?.let { id ->
                layersList.indexOfFirst { it.id == id }
            } ?: -1
        activeLayerIndex =
            if (preservedIndex >= 0) {
                preservedIndex
            } else {
                activeLayerIndex.coerceIn(
                    0,
                    (layersList.size - 1).coerceAtLeast(0)
                )
            }
        cropRect.set(
            0f,
            0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat()
        )
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
        invalidate()
    }
    fun saveCurrentStateToDiskAsync() {
        if (layersList.isEmpty() ||
            projectId.isEmpty()
        ) {
            return
        }
        if (!isValidCanvasSize(
                canvasWidth,
                canvasHeight
            )
        ) {
            return
        }
        val compositeBmp =
            createSafeBitmap(
                canvasWidth,
                canvasHeight
            ) ?: return
        val compCanvas =
            Canvas(compositeBmp)
        layersList.forEach { layer ->
            if (layer.isVisible) {
                compCanvas.drawBitmap(
                    layer.bitmap,
                    0f,
                    0f,
                    null
                )
            }
        }
        saveExecutor.execute {
            try {
                CanvasProjectManager
                    .autoSaveCanvasWorkspace(
                        context,
                        projectId,
                        projectName,
                        canvasWidth,
                        canvasHeight,
                        compositeBmp
                    )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (!compositeBmp.isRecycled) {
                    compositeBmp.recycle()
                }
            }
        }
    }
    private fun notifyHistoryListener() {
        val canUndo =
            undoActions.size > 1 ||
                (
                    undoActions.size == 1 &&
                        undoActions.first() !is
                        DrawAction.BaseImage
                    )
        historyListener?.onHistoryChanged(
            canUndo,
            redoActions.isNotEmpty()
        )
    }
    fun releaseMemoryOnDestroy() {
        saveExecutor.shutdown()
        try {
            saveExecutor.awaitTermination(
                2,
                java.util.concurrent.TimeUnit.SECONDS
            )
        } catch (e: Exception) {
        }
        layersList.forEach {
            if (!it.bitmap.isRecycled) {
                it.bitmap.recycle()
            }
        }
        layersList.clear()
        undoActions.forEach { action ->
            when (action) {
                is DrawAction.BaseImage -> {
                    if (!action.bitmap.isRecycled) {
                        action.bitmap.recycle()
                    }
                }
                is DrawAction.ImageStamp -> {
                    if (!action.bitmap.isRecycled) {
                        action.bitmap.recycle()
                    }
                }
                is DrawAction.ApplyFilter -> {
                    if (!action.resultBitmap.isRecycled) {
                        action.resultBitmap.recycle()
                    }
                }
                is DrawAction.CanvasResize -> {
                    action.beforeSnapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    action.afterSnapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                }
                is DrawAction.DeleteLayer -> {
                    if (!action.cachedBitmapSnapshot.isRecycled) {
                        action.cachedBitmapSnapshot.recycle()
                    }
                }
                else -> {}
            }
        }
        redoActions.forEach { action ->
            when (action) {
                is DrawAction.BaseImage -> {
                    if (!action.bitmap.isRecycled) {
                        action.bitmap.recycle()
                    }
                }
                is DrawAction.ImageStamp -> {
                    if (!action.bitmap.isRecycled) {
                        action.bitmap.recycle()
                    }
                }
                is DrawAction.ApplyFilter -> {
                    if (!action.resultBitmap.isRecycled) {
                        action.resultBitmap.recycle()
                    }
                }
                is DrawAction.CanvasResize -> {
                    action.beforeSnapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                    action.afterSnapshots.values.forEach {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                }
                is DrawAction.DeleteLayer -> {
                    if (!action.cachedBitmapSnapshot.isRecycled) {
                        action.cachedBitmapSnapshot.recycle()
                    }
                }
                else -> {}
            }
        }
        undoActions.clear()
        redoActions.clear()
        stampBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        stampBitmap = null
        clipboardBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        clipboardBitmap = null
        System.gc()
    }
    private fun drawInteractiveFrame(
        canvas: Canvas,
        rect: RectF,
        matrix: Matrix?,
        isCropTool: Boolean = false,
        hasTopRotationStem: Boolean = false
    ) {
        val handles =
            get12HandlePoints(
                rect,
                hasTopRotationStem
            )
        val pts = FloatArray(24)
        for (i in 0 until 12) {
            pts[i * 2] =
                handles[i].x
            pts[i * 2 + 1] =
                handles[i].y
        }
        matrix?.mapPoints(pts)
        val path =
            Path().apply {
                moveTo(
                    pts[0],
                    pts[1]
                )
                lineTo(
                    pts[4],
                    pts[5]
                )
                lineTo(
                    pts[8],
                    pts[9]
                )
                lineTo(
                    pts[12],
                    pts[13]
                )
                close()
            }
        canvas.drawPath(
            path,
            transformFramePaint
        )
        if (hasTopRotationStem) {
            val topCenterX = pts[2]
            val topCenterY = pts[3]
            val rotX = pts[16]
            val rotY = pts[17]
            canvas.drawLine(
                topCenterX,
                topCenterY,
                rotX,
                rotY,
                transformFramePaint
            )
            canvas.drawCircle(
                rotX,
                rotY,
                12f,
                rotationHandlePaint
            )
            canvas.drawCircle(
                rotX,
                rotY,
                12f,
                transformFramePaint
            )
        }
        val maxDrawHandles =
            if (isCropTool) 8 else 8
        for (i in 0 until maxDrawHandles) {
            val hx =
                pts[i * 2]
            val hy =
                pts[i * 2 + 1]
            canvas.drawCircle(
                hx,
                hy,
                12f,
                transformHandlePaint
            )
            canvas.drawCircle(
                hx,
                hy,
                12f,
                transformFramePaint
            )
        }
        if (!isCropTool &&
            !hasTopRotationStem
        ) {
            for (i in 8 until 12) {
                val hx =
                    pts[i * 2]
                val hy =
                    pts[i * 2 + 1]
                canvas.drawCircle(
                    hx,
                    hy,
                    12f,
                    rotationHandlePaint
                )
                canvas.drawCircle(
                    hx,
                    hy,
                    12f,
                    transformFramePaint
                )
            }
        }
    }
    private fun drawCanvasContent(
        canvas: Canvas
    ) {
        canvas.drawRect(
            0f,
            0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat(),
            checkerPaint
        )
        layersList.forEachIndexed { idx, layer ->
            if (layer.isVisible) {
                layerBlendPaint.alpha =
                    layer.alpha
                if (idx ==
                    activeLayerIndex &&
                    currentTool ==
                    ToolMode.TRANSFORM
                ) {
                    canvas.drawBitmap(
                        layer.bitmap,
                        layerTransformMatrix,
                        layerBlendPaint
                    )
                } else {
                    canvas.drawBitmap(
                        layer.bitmap,
                        0f,
                        0f,
                        layerBlendPaint
                    )
                }
            }
        }
        if (currentTool ==
            ToolMode.STAMP_IMAGE
        ) {
            stampBitmap?.let { bmp ->
                canvas.save()
                canvas.concat(
                    stampMatrix
                )
                canvas.drawBitmap(
                    bmp,
                    -bmp.width / 2f,
                    -bmp.height / 2f,
                    Paint(
                        Paint.ANTI_ALIAS_FLAG
                    )
                )
                canvas.restore()
                drawInteractiveFrame(
                    canvas,
                    stampBounds,
                    stampMatrix,
                    hasTopRotationStem = true
                )
            }
        } else if (currentTool ==
            ToolMode.STAMP_TEXT
        ) {
            val tPaint =
                Paint().apply {
                    color = brushColor
                    textSize = stampTextSize
                    typeface = stampTypeface
                    isAntiAlias = true
                }
            canvas.save()
            canvas.concat(
                stampMatrix
            )
            val lines =
                stampText.split("\n")
            var cy =
                stampBounds.top +
                    tPaint.textSize
            lines.forEach { line ->
                canvas.drawText(
                    line,
                    stampBounds.left,
                    cy,
                    tPaint
                )
                cy +=
                    tPaint.fontSpacing
            }
            canvas.restore()
            drawInteractiveFrame(
                canvas,
                stampBounds,
                stampMatrix,
                hasTopRotationStem = true
            )
        }
        if (symmetryMode !=
            SymmetryMode.NONE
        ) {
            when (symmetryMode) {
                SymmetryMode.HORIZONTAL -> {
                    canvas.drawLine(
                        0f,
                        canvasHeight / 2f,
                        canvasWidth.toFloat(),
                        canvasHeight / 2f,
                        symmetryGuidePaint
                    )
                }
                SymmetryMode.VERTICAL -> {
                    canvas.drawLine(
                        canvasWidth / 2f,
                        0f,
                        canvasWidth / 2f,
                        canvasHeight.toFloat(),
                        symmetryGuidePaint
                    )
                }
                SymmetryMode.RADIAL -> {
                    val angle =
                        360f /
                            symmetryRadialSectors
                    canvas.save()
                    for (i in 0 until
                        symmetryRadialSectors
                    ) {
                        canvas.drawLine(
                            canvasWidth / 2f,
                            canvasHeight / 2f,
                            canvasWidth.toFloat(),
                            canvasHeight / 2f,
                            symmetryGuidePaint
                        )
                        canvas.rotate(
                            angle,
                            canvasWidth / 2f,
                            canvasHeight / 2f
                        )
                    }
                    canvas.restore()
                }
                else -> {}
            }
        }
        if (currentTool ==
            ToolMode.PEN_VECTOR &&
            vectorPoints.isNotEmpty()
        ) {
            setupPaintForCurrentTool()
            canvas.drawPath(
                vectorPath,
                drawPaint
            )
            val vPaint =
                Paint().apply {
                    color = accentColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            vectorPoints.forEach { pt ->
                canvas.drawCircle(
                    pt.x,
                    pt.y,
                    8f,
                    vPaint
                )
            }
        }
        if (currentTool ==
            ToolMode.TRANSFORM
        ) {
            drawInteractiveFrame(
                canvas,
                transformBounds,
                layerTransformMatrix,
                hasTopRotationStem = true
            )
        }
        if (currentTool ==
            ToolMode.CROP
        ) {
            if (cropRect.isEmpty) {
                cropRect.set(
                    0f,
                    0f,
                    canvasWidth.toFloat(),
                    canvasHeight.toFloat()
                )
            }
            drawInteractiveFrame(
                canvas,
                cropRect,
                null,
                isCropTool = true
            )
        }
        if (isDrawingShape &&
            activeLayerIndex <
            layersList.size
        ) {
            setupPaintForCurrentTool()
            when (currentTool) {
                ToolMode.LINE,
                ToolMode.RECTANGLE,
                ToolMode.CIRCLE,
                ToolMode.ARROW -> {
                    drawShape(
                        canvas,
                        currentTool,
                        startX,
                        startY,
                        currentX,
                        currentY,
                        drawPaint
                    )
                }
                ToolMode.GRADIENT -> {
                    val gPaint =
                        Paint().apply {
                            strokeWidth = 2f
                            color = Color.BLACK
                            style =
                                Paint.Style.STROKE
                            pathEffect =
                                DashPathEffect(
                                    floatArrayOf(
                                        10f,
                                        10f
                                    ),
                                    0f
                                )
                        }
                    canvas.drawLine(
                        startX,
                        startY,
                        currentX,
                        currentY,
                        gPaint
                    )
                }
                ToolMode.SELECT_RECT -> {
                    canvas.drawRect(
                        Math.min(
                            startX,
                            currentX
                        ),
                        Math.min(
                            startY,
                            currentY
                        ),
                        Math.max(
                            startX,
                            currentX
                        ),
                        Math.max(
                            startY,
                            currentY
                        ),
                        selectionBorderPaint
                    )
                    canvas.drawRect(
                        Math.min(
                            startX,
                            currentX
                        ),
                        Math.min(
                            startY,
                            currentY
                        ),
                        Math.max(
                            startX,
                            currentX
                        ),
                        Math.max(
                            startY,
                            currentY
                        ),
                        selectionPaint
                    )
                }
                ToolMode.LASSO -> {
                    canvas.drawPath(
                        currentDrawPath,
                        selectionBorderPaint
                    )
                    canvas.drawPath(
                        currentDrawPath,
                        selectionPaint
                    )
                }
                else -> {}
            }
        }
        if (isSelectionActive) {
            canvas.drawPath(
                selectionPath,
                selectionBorderPaint
            )
            canvas.drawPath(
                selectionPath,
                selectionPaint
            )
        }
        if (!isTransforming &&
            currentTool != ToolMode.FILL &&
            currentTool != ToolMode.PAN &&
            currentTool != ToolMode.PIPPETE &&
            currentTool != ToolMode.LINE &&
            currentTool != ToolMode.RECTANGLE &&
            currentTool != ToolMode.CIRCLE &&
            currentTool != ToolMode.ARROW &&
            currentTool != ToolMode.GRADIENT &&
            currentTool != ToolMode.LASSO &&
            currentTool != ToolMode.SELECT_RECT &&
            currentTool != ToolMode.TRANSFORM &&
            currentTool != ToolMode.CROP &&
            currentTool != ToolMode.PEN_VECTOR &&
            currentTool != ToolMode.STAMP_IMAGE &&
            currentTool != ToolMode.STAMP_TEXT &&
            activeLayerIndex <
            layersList.size
        ) {
            val isEraser =
                currentTool ==
                    ToolMode.ERASER
            val livePaint =
                Paint().apply {
                    isAntiAlias = true
                    style =
                        Paint.Style.STROKE
                    strokeJoin =
                        Paint.Join.ROUND
                    strokeCap =
                        Paint.Cap.ROUND
                    strokeWidth =
                        if (isEraser) {
                            eraserSize
                        } else {
                            brushSize
                        }
                    color =
                        if (isEraser) {
                            Color.TRANSPARENT
                        } else {
                            brushColor
                        }
                    xfermode =
                        if (isEraser) {
                            PorterDuffXfermode(
                                PorterDuff.Mode.CLEAR
                            )
                        } else {
                            null
                        }
                    if (currentTool ==
                        ToolMode.BLUR
                    ) {
                        maskFilter =
                            BlurMaskFilter(
                                Math.max(
                                    brushSize,
                                    1f
                                ),
                                BlurMaskFilter.Blur.NORMAL
                            )
                    }
                }
            if (isSelectionActive) {
                canvas.save()
                canvas.clipPath(
                    selectionPath
                )
            }
            canvas.drawPath(
                currentDrawPath,
                livePaint
            )
            if (isSelectionActive) {
                canvas.restore()
            }
        }
        canvas.drawRect(
            0f,
            0f,
            canvasWidth.toFloat(),
            canvasHeight.toFloat(),
            borderPaint
        )
    }
    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)
        canvas.drawColor(
            Color.parseColor(
                "#FF1C1B1F"
            )
        )
        canvas.save()
        canvas.concat(
            canvasMatrix
        )
        drawCanvasContent(canvas)
        canvas.restore()
        if (isMagnifierEnabled &&
            isTouchingCanvas &&
            isStrokeTool(currentTool)
        ) {
            val cx =
                magnifierMarginLeft +
                    magnifierRadius
            val cy =
                magnifierMarginTop +
                    magnifierRadius
            magnifierPath.reset()
            magnifierPath.addCircle(
                cx,
                cy,
                magnifierRadius,
                Path.Direction.CW
            )
            canvas.save()
            canvas.clipPath(
                magnifierPath
            )
            canvas.drawColor(
                Color.parseColor(
                    "#FF1C1B1F"
                )
            )
            magnifierMatrix.set(
                canvasMatrix
            )
            magnifierMatrix.postScale(
                magnifierFactor,
                magnifierFactor,
                touchRawX,
                touchRawY
            )
            magnifierMatrix.postTranslate(
                cx - touchRawX,
                cy - touchRawY
            )
            canvas.save()
            canvas.concat(
                magnifierMatrix
            )
            drawCanvasContent(canvas)
            canvas.restore()
            canvas.drawLine(
                cx - 20f,
                cy,
                cx + 20f,
                cy,
                magnifierCrosshairPaint
            )
            canvas.drawLine(
                cx,
                cy - 20f,
                cx,
                cy + 20f,
                magnifierCrosshairPaint
            )
            canvas.restore()
            canvas.drawCircle(
                cx,
                cy,
                magnifierRadius,
                magnifierBorderPaint
            )
        }
    }
   fun setLayerVisibilityFromUI(index: Int, visible: Boolean) {
    if (index !in layersList.indices) return
    beginLayerHistoryChange()
    layersList[index].isVisible = visible
    invalidate()
    commitLayerHistoryChange()
   }
   fun deleteLayerFromUI(index: Int) {
    if (layersList.size <= 1) return
    if (index !in layersList.indices) return
    beginLayerHistoryChange()
    layersList.removeAt(index)
    activeLayerIndex = when {
        layersList.isEmpty() -> 0
        activeLayerIndex > index -> activeLayerIndex - 1
        activeLayerIndex >= layersList.size -> layersList.size - 1
        else -> activeLayerIndex
    }
    invalidate()
    commitLayerHistoryChange()
   }
   fun addLayerFromUI() {
    val bitmap = Bitmap.createBitmap(
        canvasWidth,
        canvasHeight,
        Bitmap.Config.ARGB_8888
    )
    val id = "layer_${System.currentTimeMillis()}"
    val name = "Layer ${layersList.size + 1}"
    beginLayerHistoryChange()
    layersList.add(
        0,
        CanvasLayer(
            id,
            name,
            bitmap
        )
    )
    activeLayerIndex = 0
    invalidate()
    commitLayerHistoryChange()
   }
   fun setLayerAlphaFromUI(index: Int, alpha: Int) {
    if (index !in layersList.indices) return
    layersList[index].alpha = alpha.coerceIn(0, 255)
    invalidate()
   }
   fun historyLogCurrentLayerState() {
    commitLayerHistoryChange()
   }
   private fun beginLayerHistoryChange() {
    layerHistoryBefore = captureCurrentLayerState()
   }
   private fun captureCurrentLayerState(): DrawAction.LayerState {
    val items = ArrayList<DrawAction.LayerStateItem>()
    for (layer in layersList) {
        items.add(
            DrawAction.LayerStateItem(
                id = layer.id,
                name = layer.name,
                bitmap = layer.bitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    true
                ),
                alpha = layer.alpha,
                isVisible = layer.isVisible
            )
        )
    }
    return DrawAction.LayerState(
        items = items,
        activeLayerIndex = activeLayerIndex
    )
   }
   private fun commitLayerHistoryChange() {
    val before = layerHistoryBefore ?: return
    val after = captureCurrentLayerState()
    if (!layerStatesEqual(before, after)) {
        undoActions.add(
            DrawAction.LayerStateChange(
                before,
                after
            )
        )
        redoActions.clear()
        notifyHistoryListener()
    }
    layerHistoryBefore = null
   }
   private fun restoreLayerState(state: DrawAction.LayerState) {
    val preservedActiveLayerId =
        layersList.getOrNull(activeLayerIndex)?.id
    layersList.clear()
    for (item in state.items) {
        layersList.add(
            CanvasLayer(
                item.id,
                item.name,
                item.bitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    true
                )
            ).apply {
                alpha = item.alpha
                isVisible = item.isVisible
            }
        )
    }
    val preservedIndex =
        preservedActiveLayerId?.let { id ->
            layersList.indexOfFirst { it.id == id }
        } ?: -1
    activeLayerIndex =
        if (preservedIndex >= 0) {
            preservedIndex
        } else {
            state.activeLayerIndex.coerceIn(
                0,
                (layersList.size - 1).coerceAtLeast(0)
            )
        }
    invalidate()
   }
   private fun layerStatesEqual(
    a: DrawAction.LayerState,
    b: DrawAction.LayerState
    ): Boolean {
    if (a.items.size != b.items.size) return false
    for (i in a.items.indices) {
        val x = a.items[i]
        val y = b.items[i]
        if (x.id != y.id) return false
        if (x.name != y.name) return false
        if (x.alpha != y.alpha) return false
        if (x.isVisible != y.isVisible) return false
        if (x.bitmap.width != y.bitmap.width ||
            x.bitmap.height != y.bitmap.height) {
            return false
        }
        if (!x.bitmap.sameAs(y.bitmap)) return false
    }
    return true
   }
   fun getActiveLayerThumbnail(size: Int = 72): Bitmap? {
        if (layersList.isEmpty() || activeLayerIndex !in layersList.indices) return null
        val source = layersList[activeLayerIndex].bitmap
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        val side = size.coerceAtLeast(1)
        val result = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(result)
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val scale = minOf(
            side.toFloat() / source.width.toFloat(),
            side.toFloat() / source.height.toFloat()
        )
        val w = source.width * scale
        val h = source.height * scale
        val dst = RectF((side - w) / 2f, (side - h) / 2f, (side + w) / 2f, (side + h) / 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        c.drawBitmap(source, null, dst, paint)
        return result
   }
}
