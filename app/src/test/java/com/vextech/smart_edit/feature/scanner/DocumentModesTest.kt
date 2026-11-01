package com.vextech.smart_edit.feature.scanner

import com.google.common.truth.Truth.assertThat
import com.vextech.smart_edit.feature.scanner.domain.*
import org.junit.Test

class DocumentModesTest {
    @Test fun twoCardsStayOnThePageWithoutOverlapping() {
        val s = IdCardLayout.slots(2)
        assertThat(s).hasSize(2)
        assertThat(s[0].y + s[0].h).isLessThan(s[1].y)
        s.forEach {
            assertThat(it.x).isAtLeast(0); assertThat(it.x + it.w).isAtMost(IdCardLayout.PAGE_W)
            assertThat(it.y).isAtLeast(0); assertThat(it.y + it.h).isAtMost(IdCardLayout.PAGE_H)
        }
    }

    @Test fun pictureIsFittedWithoutDistortion() {
        val slot = IdCardLayout.slots(1).single()
        val r = IdCardLayout.fit(slot, 2000, 1000)
        assertThat(r.w).isEqualTo(slot.w)
        assertThat(r.h).isEqualTo(505)
        assertThat(r.y).isEqualTo(slot.y + (slot.h - r.h) / 2)
    }

    @Test fun uprightCardIsTurnedToLandscape() {
        assertThat(IdCardLayout.needsTurn(600, 900)).isTrue()
        assertThat(IdCardLayout.needsTurn(900, 600)).isFalse()
    }

    @Test fun gutterIsFoundNearTheDarkLineElseMiddle() {
        val w = 200; val h = 120
        val spread = LumaFrame(w, h, IntArray(w * h) { i -> if (i % w in 91..93) 70 else 215 })
        assertThat(SpreadSplitter.gutter(spread)).isWithin(0.02f).of(0.465f)
        assertThat(SpreadSplitter.gutter(LumaFrame(w, h, IntArray(w * h) { 200 }))).isEqualTo(0.5f)
    }

    @Test fun bookKeepsTheWholeSpreadAndSmallSubjectsNeedLowerMinimums() {
        assertThat(ScanMode.BOOK.crops).isFalse()
        assertThat(ScanMode.RECEIPT.minArea).isLessThan(ScanMode.DOCUMENT.minArea)
        assertThat(ScanMode.ID_CARD.minArea).isLessThan(ScanMode.DOCUMENT.minArea)
    }
}
