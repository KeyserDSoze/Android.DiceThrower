package com.keyserdsoze.dicethrower.ui.v2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Reproducible full-resolution fantasy table artwork. Every line, rune and texture mark is
 * rendered at the target resolution; the tiny legacy 135x240 thumbnails are not scaled up.
 * This purely visual RNG is seeded per theme and is independent of the dice engine.
 */
internal object FantasyTableArtwork {
    const val FULL_WIDTH = 1080
    const val FULL_HEIGHT = 1920
    const val PREVIEW_WIDTH = 540
    const val PREVIEW_HEIGHT = 960

    fun supports(theme: DiceTableTheme) = when (theme) {
        DiceTableTheme.TAVERN_WOOD, DiceTableTheme.DUNGEON_STONE,
        DiceTableTheme.ELVEN_GROVE, DiceTableTheme.FROZEN_REALM,
        DiceTableTheme.DESERT_RUINS, DiceTableTheme.ASTRAL_VOID -> true
        else -> false
    }

    fun render(theme: DiceTableTheme, preview: Boolean = false): Bitmap {
        require(supports(theme)) { "No generated artwork for $theme" }
        val width = if (preview) PREVIEW_WIDTH else FULL_WIDTH
        val height = if (preview) PREVIEW_HEIGHT else FULL_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.scale(width / FULL_WIDTH.toFloat(), height / FULL_HEIGHT.toFloat())
        Painter(canvas, Random(theme.ordinal * 34171 + 9031)).apply {
            when (theme) {
                DiceTableTheme.TAVERN_WOOD -> tavern()
                DiceTableTheme.DUNGEON_STONE -> dungeon()
                DiceTableTheme.ELVEN_GROVE -> elven()
                DiceTableTheme.FROZEN_REALM -> frozen()
                DiceTableTheme.DESERT_RUINS -> desert()
                DiceTableTheme.ASTRAL_VOID -> astral()
                else -> Unit
            }
            finish()
            // Paint the physical tabletop rim last: the vignette must not swallow it.
            tabletopFrame(theme)
        }
        return bitmap
    }

    private class Painter(private val canvas: Canvas, private val rng: Random) {
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bounds = RectF(0f, 0f, FULL_WIDTH.toFloat(), FULL_HEIGHT.toFloat())
        private fun rgb(hex: Long) = hex.toInt()
        private fun tint(color: Int, alpha: Int) = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

        private fun fill(color: Int, alpha: Int = 255) {
            brush.reset()
            brush.isAntiAlias = true
            brush.style = Paint.Style.FILL
            brush.color = tint(color, alpha)
        }

        private fun stroke(color: Int, width: Float, alpha: Int = 255) {
            fill(color, alpha)
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = width
            brush.strokeCap = Paint.Cap.ROUND
            brush.strokeJoin = Paint.Join.ROUND
        }

        private fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, alpha: Int = 255) {
            fill(color, alpha); canvas.drawRect(l, t, r, b, brush)
        }
        private fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float = 2f, alpha: Int = 255) {
            stroke(color, width, alpha); canvas.drawLine(x1, y1, x2, y2, brush)
        }
        private fun circle(x: Float, y: Float, radius: Float, color: Int, alpha: Int = 255) {
            fill(color, alpha); canvas.drawCircle(x, y, radius, brush)
        }
        private fun ring(x: Float, y: Float, radius: Float, color: Int, width: Float, alpha: Int = 255) {
            stroke(color, width, alpha); canvas.drawCircle(x, y, radius, brush)
        }
        private fun ellipse(x: Float, y: Float, rx: Float, ry: Float, color: Int, alpha: Int = 255) {
            fill(color, alpha); canvas.drawOval(x-rx, y-ry, x+rx, y+ry, brush)
        }
        private fun gradient(top: Int, bottom: Int) {
            fill(Color.WHITE)
            brush.shader = LinearGradient(0f, 0f, 0f, 1920f, top, bottom, Shader.TileMode.CLAMP)
            canvas.drawRect(bounds, brush)
            brush.shader = null
        }
        private fun polygon(points: List<Pair<Float, Float>>, color: Int, alpha: Int = 255, outline: Int? = null) {
            if (points.size < 3) return
            val path = Path().apply {
                moveTo(points[0].first, points[0].second)
                points.drop(1).forEach { lineTo(it.first, it.second) }
                close()
            }
            fill(color, alpha); canvas.drawPath(path, brush)
            if (outline != null) { stroke(outline, 2.5f, 135); canvas.drawPath(path, brush) }
        }
        private fun curve(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float,
                          color: Int, width: Float = 2f, alpha: Int = 255) {
            val path = Path().apply { moveTo(x1, y1); quadTo(cx, cy, x2, y2) }
            stroke(color, width, alpha); canvas.drawPath(path, brush)
        }
        private fun noise(color: Int, count: Int, maxSize: Float, alpha: Int) {
            repeat(count) {
                val x = rng.nextFloat()*1080f
                val y = rng.nextFloat()*1920f
                circle(x, y, 0.5f+rng.nextFloat()*maxSize, color, rng.nextInt((alpha/3).coerceAtLeast(1), alpha+1))
            }
        }
        private fun compass(cx: Float, cy: Float, r: Float, ink: Int) {
            ring(cx,cy,r,ink,3.2f,90)
            ring(cx,cy,r*0.75f,ink,1.6f,95)
            for (i in 0..7) {
                val a = Math.PI * i/4.0
                val dx=sin(a).toFloat(); val dy=cos(a).toFloat()
                polygon(listOf(
                    cx+dx*r*1.16f to cy+dy*r*1.16f,
                    cx-dy*r*.16f to cy+dx*r*.16f,
                    cx-dx*r*.13f to cy-dy*r*.13f,
                    cx+dy*r*.16f to cy-dx*r*.16f
                ), ink, if(i%2==0) 125 else 65)
            }
            circle(cx,cy,r*.08f,ink,120)
        }
        private fun runes(cx: Float, cy: Float, radius: Float, ink: Int, alpha: Int) {
            for (i in 0 until 24) {
                val a=2.0*Math.PI*i/24.0
                val x=cx+radius*cos(a).toFloat(); val y=cy+radius*sin(a).toFloat()
                val dx=cos(a).toFloat(); val dy=sin(a).toFloat()
                line(x-dy*12f,y+dx*12f,x+dy*12f,y-dx*12f,ink,3.2f,alpha)
                if(i%3==0) line(x,y,x+dx*20f+dy*10f,y+dy*20f-dx*10f,ink,3f,alpha)
            }
        }
        private fun border(ink: Int, glow: Int) {
            for (i in 0..3) {
                val inset = 16f+i*11f
                stroke(ink, if(i==0) 9f else 2f, if(i==0) 195 else 95)
                canvas.drawRoundRect(inset,inset,1080f-inset,1920f-inset,28f,28f,brush)
            }
            for (x in listOf(45f,1035f)) {
                for (y in 95..1825 step 145) {
                    circle(x,y.toFloat(),7.3f,glow,190)
                    circle(x-1f,y-1f,2.8f,Color.WHITE,110)
                }
            }
        }
        /**
         * A readable, inset tabletop silhouette shared by the premium collection.
         *
         * Each scene keeps its own surface, materials and storytelling; only the
         * geometry of the raised playable rim is consistent with the original tables.
         * This is intentionally drawn AFTER the vignette so the frame survives the
         * preview crop and remains visible around the dice on dark displays.
         */
        fun tabletopFrame(theme: DiceTableTheme) {
            val palette = when (theme) {
                DiceTableTheme.TAVERN_WOOD -> intArrayOf(
                    rgb(0x402211), rgb(0x966039), rgb(0xE5BA75), rgb(0xD5A25C),
                )
                DiceTableTheme.DUNGEON_STONE -> intArrayOf(
                    rgb(0x262B2B), rgb(0x69746E), rgb(0xBAC4A7), rgb(0xB29B6B),
                )
                DiceTableTheme.ELVEN_GROVE -> intArrayOf(
                    rgb(0x193629), rgb(0x56865C), rgb(0xC0D99C), rgb(0xD5BE78),
                )
                DiceTableTheme.FROZEN_REALM -> intArrayOf(
                    rgb(0x10334E), rgb(0x469CBF), rgb(0xC6F4FF), rgb(0xA5EAFF),
                )
                DiceTableTheme.DESERT_RUINS -> intArrayOf(
                    rgb(0x50301D), rgb(0xAB8150), rgb(0xF3D69B), rgb(0x9B6336),
                )
                DiceTableTheme.ASTRAL_VOID -> intArrayOf(
                    rgb(0x1E1A43), rgb(0x6655AB), rgb(0xD6C4FF), rgb(0x92CBFF),
                )
                else -> return
            }
            val dark = palette[0]
            val middle = palette[1]
            val light = palette[2]
            val ornament = palette[3]

            // Match the first four presets: a quieter, recessed play surface
            // surrounded by an unmistakable physical rim. Each tone remains
            // specific to the table's material, and the original texture stays
            // visible beneath this translucent finish.
            val playSurface = when (theme) {
                DiceTableTheme.TAVERN_WOOD -> rgb(0x301B11)
                DiceTableTheme.DUNGEON_STONE -> rgb(0x242A29)
                DiceTableTheme.ELVEN_GROVE -> rgb(0x153924)
                DiceTableTheme.FROZEN_REALM -> rgb(0x103957)
                DiceTableTheme.DESERT_RUINS -> rgb(0xE4CB9A)
                DiceTableTheme.ASTRAL_VOID -> rgb(0x101134)
                else -> dark
            }
            fill(playSurface, if (theme == DiceTableTheme.DESERT_RUINS) 58 else 90)
            canvas.drawRoundRect(130f, 186f, 950f, 1734f, 26f, 26f, brush)

            fun outline(insetX: Float, insetY: Float, width: Float, color: Int, alpha: Int) {
                stroke(color, width, alpha)
                canvas.drawRoundRect(
                    insetX, insetY, FULL_WIDTH - insetX, FULL_HEIGHT - insetY,
                    28f, 28f, brush,
                )
            }
            // Four concentric bevels make this a *raised frame*, not wallpaper.
            outline(86f, 148f, 68f, Color.BLACK, 125) // cast shadow
            outline(88f, 146f, 47f, dark, 248)        // deep outer rail
            outline(91f, 149f, 26f, middle, 240)      // material face
            outline(111f, 168f, 7f, light, 235)       // lit inner edge
            outline(123f, 180f, 3f, dark, 180)        // transition to felt/stone/ice
            // Corners always show the table bounds even in small previews.
            for (x in listOf(90f, 990f)) {
                for (y in listOf(148f, 1772f)) {
                    circle(x, y, 24f, dark, 255)
                    ring(x, y, 18f, light, 5f, 200)
                    circle(x, y, 7f, ornament, 240)
                    circle(x - 2f, y - 2f, 2.3f, Color.WHITE, 185)
                }
            }
            // Decoration stays ON the rim; the center remains free for 3D dice.
            for (i in 0..10) {
                val y = 275f + i * 137f
                when (theme) {
                    DiceTableTheme.TAVERN_WOOD -> {
                        // Grain, iron joints and polished brass studs.
                        line(83f, y - 35f, 83f, y + 39f, light, 2.8f, 120)
                        line(997f, y - 35f, 997f, y + 39f, light, 2.8f, 120)
                        circle(91f, y, 6.5f, ornament, 245)
                        circle(989f, y, 6.5f, ornament, 245)
                        line(71f, y + 45f, 111f, y + 45f, dark, 6f, 170)
                        line(969f, y + 45f, 1009f, y + 45f, dark, 6f, 170)
                    }
                    DiceTableTheme.DUNGEON_STONE -> {
                        // Rough block joints and chiselled rune notches.
                        line(68f, y + 39f, 109f, y + 39f, dark, 9f, 185)
                        line(971f, y + 39f, 1012f, y + 39f, dark, 9f, 185)
                        line(85f, y - 15f, 97f, y, ornament, 4f, 200)
                        line(995f, y - 15f, 983f, y, ornament, 4f, 200)
                    }
                    DiceTableTheme.ELVEN_GROVE -> {
                        // Climbing vines and leaf clusters, not generic metal studs.
                        curve(86f, y - 59f, 121f, y - 6f, 87f, y + 52f, light, 3.5f, 205)
                        curve(994f, y - 59f, 959f, y - 6f, 993f, y + 52f, light, 3.5f, 205)
                        ellipse(100f, y - 16f, 12f, 6f, ornament, 190)
                        ellipse(980f, y + 16f, 12f, 6f, ornament, 190)
                    }
                    DiceTableTheme.FROZEN_REALM -> {
                        // Faceted ice facets alternating with frosted crystal glints.
                        polygon(listOf(74f to y - 39f, 110f to y, 74f to y + 32f), light, 140)
                        polygon(listOf(1006f to y - 39f, 970f to y, 1006f to y + 32f), light, 140)
                        line(80f, y - 40f, 100f, y + 22f, Color.WHITE, 2f, 145)
                        line(1000f, y - 40f, 980f, y + 22f, Color.WHITE, 2f, 145)
                    }
                    DiceTableTheme.DESERT_RUINS -> {
                        // Weathered sandstone courses and carved temple marks.
                        line(67f, y + 48f, 113f, y + 48f, dark, 7f, 170)
                        line(967f, y + 48f, 1013f, y + 48f, dark, 7f, 170)
                        polygon(listOf(89f to y - 23f, 77f to y + 12f, 101f to y + 12f), ornament, 190)
                        polygon(listOf(991f to y - 23f, 979f to y + 12f, 1003f to y + 12f), ornament, 190)
                    }
                    DiceTableTheme.ASTRAL_VOID -> {
                        // Luminous nodes and a delicate constellation circuit.
                        circle(91f, y, 7f, ornament, 240)
                        circle(989f, y, 7f, ornament, 240)
                        line(91f, y - 55f, 91f, y - 11f, light, 2.5f, 175)
                        line(989f, y - 55f, 989f, y - 11f, light, 2.5f, 175)
                        ring(91f, y, 15f, light, 1.8f, 165)
                        ring(989f, y, 15f, light, 1.8f, 165)
                    }
                    else -> Unit
                }
            }
            // Top/bottom rails bookend the surface without a distracting center emblem.
            for (y in listOf(147f, 1773f)) {
                line(175f, y, 905f, y, light, 2f, 105)
                line(375f, y, 705f, y, ornament, 4f, 130)
            }
        }

        private fun warmLamp(cx: Float, cy: Float) {
            fill(Color.WHITE)
            brush.shader = RadialGradient(cx,cy,245f,intArrayOf(rgb(0x88FFAF45),rgb(0x22EE7520),Color.TRANSPARENT),floatArrayOf(0f,.4f,1f),Shader.TileMode.CLAMP)
            canvas.drawCircle(cx,cy,245f,brush);brush.shader=null
            ellipse(cx,cy+7f,31f,41f,rgb(0x402008),220)
            circle(cx,cy,20f,rgb(0xFFC66C))
            circle(cx,cy-6f,12f,rgb(0xFFF2AE))
            circle(cx-3f,cy-10f,5f,Color.WHITE,200)
        }
        private fun shards(edge: Boolean, color: Int, count: Int) {
            repeat(count) {
                val x=if(edge) if(rng.nextBoolean()) rng.nextFloat()*125f else 955f+rng.nextFloat()*125f
                      else rng.nextFloat()*1080f
                val y=rng.nextFloat()*1920f
                val r=20f+rng.nextFloat()*95f
                polygon(listOf(x to y-r, x+r*.55f to y+r*.15f,
                    x to y+r*.6f, x-r*.48f to y+r*.1f), color, rng.nextInt(35,128), Color.WHITE)
                line(x,y-r,x,y+r*.58f,Color.WHITE,2.4f,80)
            }
        }

        fun tavern() {
            gradient(rgb(0x513018),rgb(0x251510))
            for (i in 0..12) {
                val y=i*160f
                rect(0f,y,1080f,y+150f,if(i%2==0)rgb(0x764520) else rgb(0x623819),225)
                line(0f,y+153f,1080f,y+153f,rgb(0x130A08),9f)
                line(0f,y+6f,1080f,y+6f,rgb(0xDDA15D),2.4f,67)
                repeat(70) {
                    val yy=y+rng.nextFloat()*146f
                    val xx=rng.nextFloat()*1080f
                    val len=40+rng.nextInt(240)
                    curve(xx,yy,xx+len*.55f,yy+rng.nextInt(-7,8),xx+len,yy+rng.nextInt(-5,6),
                        if(rng.nextBoolean())rgb(0x140C09) else rgb(0xE6AE69),rng.nextFloat()*2.7f+0.8f,35+rng.nextInt(65))
                }
                for(x in listOf(90f,990f)) {
                    circle(x,y+80f,7f,rgb(0x1A1210))
                    circle(x-1f,y+79f,2.3f,rgb(0xAE8C51),140)
                }
            }
            noise(rgb(0x100903),1100,2.5f,82)
            border(rgb(0x9E6C32),rgb(0xE7B45F))
            compass(540f,940f,250f,rgb(0xE0A659))
            for(y in listOf(190f,1730f))for(x in listOf(165f,915f))warmLamp(x,y)
        }
        fun dungeon() {
            gradient(rgb(0x515755),rgb(0x292B2D))
            for(row in 0..12) for(col in 0..5) {
                val x=col*214f-(if(row%2==0) 85f else 0f)
                val y=row*160f
                val pts=listOf(x+5f to y+6f,x+203f to y+11f,x+202f to y+146f,x+11f to y+153f)
                polygon(pts,if((row+col)%3==0)rgb(0x858B83) else rgb(0x626861),195,rgb(0x181C1B))
                repeat(13) {
                    val xx=x+15f+rng.nextFloat()*170f
                    val yy=y+18f+rng.nextFloat()*120f
                    line(xx,yy,xx+rng.nextFloat()*38f,yy+rng.nextFloat()*9f,
                        rgb(0x171A17),1.4f,75)
                }
            }
            noise(rgb(0xD5D0BF),2400,2.7f,85)
            ring(540f,970f,370f,rgb(0x111914),13f,60)
            ring(540f,970f,315f,rgb(0xB3B29E),4f,75)
            runes(540f,970f,340f,rgb(0xCABE8B),72)
            compass(540f,970f,126f,rgb(0xB3A279))
            border(rgb(0x30332F),rgb(0xA4A38C))
            for(y in listOf(140f,1780f))for(x in listOf(110f,970f))warmLamp(x,y)
        }
        fun elven() {
            gradient(rgb(0x23452F),rgb(0x13281E))
            noise(rgb(0x88AE62),4200,3.5f,70)
            // The ancient wooden grove ring surrounds an intentionally clear play area.
            ellipse(540f,950f,465f,690f,rgb(0x6E5433),190)
            ellipse(540f,950f,434f,648f,rgb(0x3D4932),245)
            for (i in 0..6) {
                stroke(rgb(0xB8A574),3.2f,45+i*4)
                canvas.drawOval(130f+i*12f,330f+i*19f,950f-i*12f,1570f-i*19f,brush)
            }
            repeat(900) {
                val side=rng.nextBoolean()
                val x=if(side)rng.nextFloat()*220f else 860f+rng.nextFloat()*220f
                val y=rng.nextFloat()*1920f
                ellipse(x,y,6f+rng.nextFloat()*17f,3f+rng.nextFloat()*12f,
                    when(rng.nextInt(5)){0->rgb(0xB4A55D);1->rgb(0x1A4B25);2->rgb(0x71963D);else->rgb(0x416E31)},
                    80+rng.nextInt(145))
            }
            ring(540f,950f,260f,rgb(0xB5DDA0),3.5f,80)
            runes(540f,950f,278f,rgb(0xC5EAA5),110)
            for (i in 0..25) {
                val a=i*Math.PI*2/26
                val x=540f+390*cos(a).toFloat()
                val y=950f+550*sin(a).toFloat()
                circle(x,y,3f+rng.nextFloat()*5f,rgb(0xC2FFD6),170)
            }
            border(rgb(0x42643B),rgb(0xB6C88B))
        }
        fun frozen() {
            gradient(rgb(0x184563),rgb(0x061F37))
            for (row in 0..16)for(col in 0..8) {
                val x=col*140f+rng.nextInt(-30,30)
                val y=row*120f+rng.nextInt(-24,24)
                val tone=if(rng.nextBoolean())rgb(0x8ED5F1) else rgb(0x2B809D)
                polygon(listOf(x to y,x+135f to y-6f,x+115f to y+110f,x-15f to y+107f),
                    tone,25+rng.nextInt(65),rgb(0x91D9E3))
            }
            noise(Color.WHITE,2800,2.3f,110)
            shards(true,rgb(0x7AE5FF),145)
            for (i in 0..85) {
                val x=rng.nextFloat()*1080f;val y=rng.nextFloat()*1920f
                val ex=x+rng.nextInt(-170,170);val ey=y+rng.nextInt(20,220)
                line(x,y,ex,ey,rgb(0xD1FAFF),1.4f,68)
                if(i%4==0)line(ex,ey,ex+30f,ey+55f,rgb(0xE3FBFF),1.1f,80)
            }
            ring(540f,950f,355f,rgb(0xC7F7FE),4f,75)
            runes(540f,950f,340f,rgb(0xB9EFFB),125)
            compass(540f,950f,180f,rgb(0xC7E8ED))
            border(rgb(0x7AC5E9),rgb(0xD4F8FF))
        }
        fun desert() {
            gradient(rgb(0xB88B50),rgb(0x674425))
            noise(rgb(0x342010),4200,2.7f,65)
            noise(rgb(0xFADE96),2200,2.3f,72)
            for (i in 0..54) {
                val y=40f+i*35f
                val x=rng.nextFloat()*800f-120f
                curve(x,y,x+290f,y-30f-rng.nextFloat()*45f,x+720f,y+rng.nextInt(-22,23),
                    if(i%2==0)rgb(0xEDC47F) else rgb(0x543821),2.5f,35+rng.nextInt(60))
            }
            // Torn antique map border: an engraved fantasy chart, no legible micro-text.
            rect(120f,180f,960f,1720f,rgb(0xDFBC83),110)
            for (inset in listOf(125f,140f,155f)) {
                stroke(rgb(0x6E4022),if(inset==125f)6f else 2f,105)
                canvas.drawRect(inset,180f+inset*.25f,1080f-inset,1920f-180f-inset*.25f,brush)
            }
            repeat(24) {
                val x=200f+rng.nextFloat()*680f;val y=290f+rng.nextFloat()*1320f
                val size=12f+rng.nextFloat()*38f
                polygon(listOf(x-size to y+size,x to y-size*.8f,x+size to y+size),rgb(0x613A23),75)
                line(x-size*1.6f,y+size*1.4f,x+size*1.6f,y+size*1.4f,rgb(0x583C28),2f,88)
            }
            compass(752f,470f,128f,rgb(0x573B21))
            compass(330f,1475f,64f,rgb(0x573B21))
            border(rgb(0x71411F),rgb(0xD8B47F))
        }
        fun astral() {
            gradient(rgb(0x120F30),rgb(0x050C23))
            val cx=540f;val cy=950f
            repeat(48) {
                val x=rng.nextFloat()*1080f;val y=rng.nextFloat()*1920f
                val hue=if(rng.nextBoolean())rgb(0x7150AC) else rgb(0x3170B0)
                ellipse(x,y,70f+rng.nextFloat()*210f,80f+rng.nextFloat()*340f,
                    hue,9+rng.nextInt(22))
            }
            noise(rgb(0xA9D6FF),1700,1.3f,105)
            repeat(350) {
                val x=rng.nextFloat()*1080f;val y=rng.nextFloat()*1920f
                val r=1f+rng.nextFloat()*2.9f
                circle(x,y,r,Color.WHITE,115+rng.nextInt(140))
                if(it%11==0) {
                    line(x-10f,y,x+10f,y,rgb(0xBFD4FF),1.5f,108)
                    line(x,y-10f,x,y+10f,rgb(0xBFD4FF),1.5f,108)
                }
            }
            ring(cx,cy,377f,rgb(0xA992F7),7f,86)
            ring(cx,cy,350f,rgb(0xB8D8FF),2f,78)
            ring(cx,cy,295f,rgb(0xBF91FA),2f,76)
            runes(cx,cy,360f,rgb(0xF3C9FF),128)
            compass(cx,cy,172f,rgb(0xDCC1FF))
            for (i in 0..11) {
                val a=i*Math.PI/6
                val x=cx+310*cos(a).toFloat();val y=cy+310*sin(a).toFloat()
                circle(x,y,7f,rgb(0xD0EFFF),230)
                if(i%3==0)circle(x,y,17f,rgb(0x74A7F8),60)
            }
            border(rgb(0x65549C),rgb(0xB8A7F9))
        }
        fun finish() {
            // Screen-edge vignette unifies each theme and keeps the central dice legible.
            fill(Color.WHITE)
            brush.shader=RadialGradient(540f,920f,1140f,
                intArrayOf(Color.TRANSPARENT,Color.TRANSPARENT,rgb(0xAA03070F)),
                floatArrayOf(0f,.52f,1f),Shader.TileMode.CLAMP)
            canvas.drawRect(bounds,brush)
            brush.shader=null
        }
    }
}
