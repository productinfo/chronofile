package com.chaidarun.chronofile

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IValueFormatter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_pie.*

class PieFragment : GraphFragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.fragment_pie, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    with(pieChart) {
      animateY(800, Easing.EasingOption.EaseInOutQuad)
      description.isEnabled = false
      holeRadius = 50f
      legend.isEnabled = false
      rotationAngle = 195f
      setCenterTextColor(LABEL_COLOR)
      setCenterTextSize(LABEL_FONT_SIZE)
      setCenterTextTypeface(App.instance.typeface)
      setDrawEntryLabels(false)
      setExtraOffsets(35f, 35f, 35f, 35f)
      setHoleColor(Color.TRANSPARENT)
      setTransparentCircleAlpha(0)
    }

    // Populate form with current state
    with(Store.state) {
      when (graphConfig.metric) {
        Metric.AVERAGE -> radioAverage
        Metric.PERCENTAGE -> radioPercentage
        Metric.TOTAL -> radioTotal
      }.isChecked = true
    }

    disposables = CompositeDisposable().apply {
      add(
        Store.observable
          .filter { it.config != null && it.history != null }
          .map { Triple(it.config!!, it.history!!, it.graphConfig) }
          .distinctUntilChanged()
          .subscribe { render(it) }
      )
      add(
        Store.observable
          .map { it.graphConfig.grouped }
          .distinctUntilChanged()
          .subscribe { pieIsGrouped.isChecked = it }
      )
      add(
        Store.observable
          .map { it.graphConfig.includeSleep }
          .distinctUntilChanged()
          .subscribe { includeSleep.isChecked = it }
      )
    }
  }

  /** (Re-)renders pie chart */
  private fun render(state: Triple<Config, History, GraphConfig>) {
    val start = System.currentTimeMillis()
    val (config, history, graphConfig) = state

    // Determine date range
    val (rangeStart, rangeEnd) = getChartRange(history, graphConfig)
    val rangeSeconds = rangeEnd - rangeStart
    if (rangeSeconds <= 0) {
      App.toast("No data to show!")
      return
    }

    // Show data
    val (sliceList, totalSliceSeconds) =
      getSliceList(config, history, graphConfig, rangeStart, rangeEnd)
    val pieEntries = sliceList.map { (key, value) -> PieEntry(value.toFloat(), key) }
    val metric = graphConfig.metric
    val pieDataSet = PieDataSet(pieEntries, "Time").apply {
      colors = COLORS
      valueLineColor = Color.TRANSPARENT
      valueLinePart1Length = 0.5f
      valueLinePart2Length = 0f
      valueTextColor = LABEL_COLOR
      valueTextSize = LABEL_FONT_SIZE
      valueTypeface = App.instance.typeface
      valueFormatter = IValueFormatter { value, entry, _, _ ->
        val num: String = when (metric) {
          Metric.AVERAGE -> formatDuration(value.toLong() * DAY_SECONDS / rangeSeconds)
          Metric.PERCENTAGE -> "${value.toLong() * 100 / totalSliceSeconds}%"
          Metric.TOTAL -> formatDuration(value.toLong())
        }
        "${(entry as? PieEntry)?.label}: $num"
      }
      yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
    }

    with(pieChart) {
      centerText = "Range:\n${formatDuration(totalSliceSeconds)}"
      data = PieData(pieDataSet)
      invalidate()
    }

    val elapsed = System.currentTimeMillis() - start
    Log.i(TAG, "Rendered pie chart in $elapsed ms")
  }
}
