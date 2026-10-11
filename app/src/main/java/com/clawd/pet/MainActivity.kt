package com.clawd.pet

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var statusView: TextView
    private lateinit var statsView: TextView
    private lateinit var preview: PreviewView
    private val density by lazy { resources.displayMetrics.density }
    private val pad by lazy { (16 * density).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        prefs.ensureBorn()
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshStats()
        preview.refresh()
    }

    private fun buildUi(): View {
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(0xFF141416.toInt())
        outer.fitsSystemWindows = true

        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(pad, pad, pad, pad / 2)
        outer.addView(header)

        title(header, "Clawd")

        preview = PreviewView(this, prefs)
        val pl = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (150 * density).toInt())
        preview.layoutParams = pl
        header.addView(preview)
        statusView = note(header, "")

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        scroll.layoutParams = sp
        outer.addView(scroll)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, 0, pad, pad)
        scroll.addView(root)

        val main = section(root, "Питомец")
        switch(main, "Питомец включён", prefs.enabled) { on ->
            prefs.enabled = on
            if (on) startPet() else PetOverlayService.send(this, PetOverlayService.ACTION_STOP)
        }

        val ctrl = section(root, "Управление")
        button(ctrl, "Показать / спрятать") { ifEnabled { PetOverlayService.send(this, PetOverlayService.ACTION_TOGGLE) } }
        button(ctrl, "Позвать в центр") { ifEnabled { PetOverlayService.send(this, PetOverlayService.ACTION_RECENTER) } }
        button(ctrl, "Покормить") { ifEnabled { PetOverlayService.send(this, PetOverlayService.ACTION_FEED) } }

        val look = section(root, "Внешний вид")
        button(look, "Имя питомца") { editName() }
        selector(look, "Скин", Skins.palettes.map { it.name }, prefs.color) { prefs.color = it; refresh() }
        selector(look, "Аксессуар", Skins.accessoryNames, prefs.accessory) { prefs.accessory = it; refresh() }
        selector(look, "Размер", Skins.sizeNames, prefs.size) { prefs.size = it; refresh() }

        val beh = section(root, "Поведение")
        selector(beh, "Скорость", Skins.speedNames, prefs.speed) { prefs.speed = it; refresh() }
        switch(beh, "Гуляет сам", prefs.wander) { prefs.wander = it; refresh() }
        switch(beh, "Гравитация (ходит по полу)", prefs.gravity) { prefs.gravity = it; refresh() }
        switch(beh, "Реакция на тряску и наклон", prefs.sensors) { prefs.sensors = it; refresh() }
        switch(beh, "Упирается и хватается", prefs.cling) { prefs.cling = it; refresh() }
        switch(beh, "Угощения на экране", prefs.snacks) { prefs.snacks = it; refresh() }
        selector(beh, "Частота угощений", Skins.snackSpeedNames, prefs.snackSpeed) { prefs.snackSpeed = it; refresh() }
        selector(beh, "Засыпает", Skins.sleepNames, prefs.sleep) { prefs.sleep = it; refresh() }

        val phr = section(root, "Реплики")
        switch(phr, "Показывать реплики", prefs.phrases) { prefs.phrases = it; refresh() }
        selector(phr, "Частота реплик", Skins.phraseFreqNames, prefs.phraseFreq) { prefs.phraseFreq = it; refresh() }

        val hun = section(root, "Голод")
        switch(hun, "Питомец хочет есть", prefs.hungerEnabled) { prefs.hungerEnabled = it; refresh() }
        selector(hun, "Скорость голода", Skins.hungerNames, prefs.hungerRate) { prefs.hungerRate = it; refresh() }

        val misc = section(root, "Прочее")
        switch(misc, "Вибрация", prefs.haptics) { prefs.haptics = it }
        switch(misc, "Запуск при включении телефона", prefs.autostart) { prefs.autostart = it }

        val stat = section(root, "Статистика")
        statsView = note(stat, "")
        button(stat, "Сбросить статистику") { prefs.resetStats(); refreshStats(); preview.refresh() }

        val perm = section(root, "Разрешения")
        button(perm, "Разрешить «Поверх других приложений»") { requestOverlay() }
        button(perm, "Разрешить уведомления") { requestNotif() }

        return outer
    }

    private fun title(p: LinearLayout, t: String) {
        val tv = TextView(this)
        tv.text = t; tv.textSize = 32f; tv.setTextColor(0xFFD97757.toInt())
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding(0, 0, 0, pad / 2)
        p.addView(tv)
    }

    private fun section(root: LinearLayout, titleText: String): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val cardBg = GradientDrawable()
        cardBg.setColor(0xFF232326.toInt())
        cardBg.cornerRadius = 20f * density
        card.background = cardBg
        card.setPadding(pad, (pad * 0.8f).toInt(), pad, (pad * 0.8f).toInt())
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = (pad * 0.6f).toInt()
        card.layoutParams = lp
        val h = TextView(this)
        h.text = titleText.uppercase()
        h.textSize = 12f
        h.setTextColor(0xFFD97757.toInt())
        h.typeface = Typeface.DEFAULT_BOLD
        h.letterSpacing = 0.08f
        h.setPadding(0, 0, 0, pad / 3)
        card.addView(h)
        root.addView(card)
        return card
    }

    private fun note(p: LinearLayout, t: String): TextView {
        val tv = TextView(this)
        tv.text = t; tv.textSize = 13f; tv.setTextColor(0xFFB0B0B4.toInt())
        tv.setPadding(0, pad / 4, 0, pad / 4)
        p.addView(tv); return tv
    }
    private fun button(p: LinearLayout, t: String, onClick: () -> Unit) {
        val b = Button(this); b.text = t; b.isAllCaps = false
        b.setTextColor(Color.WHITE); b.textSize = 15f
        val bBg = GradientDrawable()
        bBg.setColor(0xFF33333A.toInt())
        bBg.cornerRadius = 14f * density
        b.background = bBg
        b.stateListAnimator = null
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = pad / 4
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        p.addView(b)
    }

    private fun switch(p: LinearLayout, t: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        val s = Switch(this)
        s.text = t; s.textSize = 15f; s.setTextColor(Color.WHITE)
        s.isChecked = initial
        s.setPadding(0, pad / 3, 0, pad / 3)
        s.setOnCheckedChangeListener { _, v -> onChange(v) }
        p.addView(s)
    }

    private fun selector(p: LinearLayout, label: String, items: List<String>, sel: Int, onSel: (Int) -> Unit) {
        var current = sel.coerceIn(0, items.size - 1)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val rowBg = GradientDrawable()
        rowBg.setColor(0xFF2C2C30.toInt())
        rowBg.cornerRadius = 14f * density
        row.background = rowBg
        row.setPadding(pad * 3 / 4, (pad * 0.7f).toInt(), pad * 3 / 4, (pad * 0.7f).toInt())
        val rlp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rlp.topMargin = pad / 3
        row.layoutParams = rlp

        val lbl = TextView(this)
        lbl.text = label; lbl.textSize = 15f; lbl.setTextColor(Color.WHITE)
        lbl.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(lbl)

        val value = TextView(this)
        value.text = items[current]; value.textSize = 14f
        value.setTextColor(0xFFD97757.toInt()); value.typeface = Typeface.DEFAULT_BOLD
        row.addView(value)

        val chev = TextView(this)
        chev.text = "  ›"; chev.textSize = 18f; chev.setTextColor(0xFF77777C.toInt())
        row.addView(chev)

        row.setOnClickListener {
            showPicker(label, items, current) { idx ->
                current = idx
                value.text = items[idx]
                onSel(idx)
            }
        }
        p.addView(row)
    }

    private fun showPicker(title: String, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val boxBg = GradientDrawable()
        boxBg.setColor(0xFF1E1E22.toInt())
        boxBg.cornerRadius = 24f * density
        box.background = boxBg
        box.setPadding(pad, pad, pad, pad / 2)

        val head = TextView(this)
        head.text = title.uppercase(); head.textSize = 12f
        head.setTextColor(0xFFD97757.toInt()); head.typeface = Typeface.DEFAULT_BOLD
        head.letterSpacing = 0.08f
        head.setPadding(pad / 3, 0, 0, pad / 2)
        box.addView(head)

        for (i in items.indices) {
            val opt = TextView(this)
            opt.textSize = 16f
            opt.setPadding(pad * 3 / 4, (pad * 0.7f).toInt(), pad * 3 / 4, (pad * 0.7f).toInt())
            val selNow = i == selected
            opt.setTextColor(if (selNow) 0xFFD97757.toInt() else Color.WHITE)
            if (selNow) {
                val ob = GradientDrawable()
                ob.setColor(0xFF2C2C30.toInt())
                ob.cornerRadius = 12f * density
                opt.background = ob
                opt.typeface = Typeface.DEFAULT_BOLD
                opt.text = "✓  ${items[i]}"
            } else {
                opt.text = items[i]
            }
            val olp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            olp.topMargin = pad / 6
            opt.layoutParams = olp
            opt.setOnClickListener { onPick(i); dlg.dismiss() }
            box.addView(opt)
        }

        val scroll = ScrollView(this)
        scroll.addView(box)
        dlg.setContentView(scroll)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dlg.show()
    }

    private fun refresh() {
        preview.refresh()
        if (prefs.enabled) PetOverlayService.send(this, PetOverlayService.ACTION_REFRESH)
    }

    private fun editName() {
        val input = EditText(this)
        input.setText(prefs.petName)
        input.setSelection(input.text.length)
        input.setSingleLine()
        val padH = pad
        val wrap = LinearLayout(this)
        wrap.setPadding(padH, pad / 2, padH, 0)
        wrap.addView(input)
        AlertDialog.Builder(this)
            .setTitle("Имя питомца")
            .setView(wrap)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim().take(16)
                if (name.isNotEmpty()) { prefs.petName = name; refresh(); refreshStats() }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun ifEnabled(block: () -> Unit) {
        if (!prefs.enabled) { toast("Сначала включи питомца"); return }
        if (!overlayOk()) { toast("Нужно разрешение «Поверх других приложений»"); requestOverlay(); return }
        block()
    }

    private fun startPet() {
        if (!overlayOk()) { requestOverlay(); return }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestNotif()
        PetOverlayService.start(this)
    }

    private fun overlayOk() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun requestOverlay() {
        if (overlayOk()) { toast("Уже разрешено"); return }
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        try { startActivity(i) } catch (_: Exception) {}
    }

    private fun requestNotif() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        else toast("Не требуется на этой версии Android")
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun refreshStatus() {
        statusView.text = if (overlayOk()) "Разрешение оверлея выдано ✓"
        else "Нужно разрешение «Поверх других приложений»"
    }

    private fun refreshStats() {
        statsView.text = "Имя: ${prefs.petName}    Возраст: ${prefs.ageDays()} дн.\n" +
            "Погладили: ${prefs.petsCount}    Покормили: ${prefs.feedCount}    Игр: ${prefs.playCount}"
    }
}
