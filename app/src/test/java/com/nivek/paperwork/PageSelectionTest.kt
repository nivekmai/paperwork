package com.nivek.paperwork

import org.junit.Assert.*
import org.junit.Test

class PageSelectionTest {
    @Test fun blankMeansAllPages() { assertEquals(listOf(0,1,2),parsePages(" ",3)) }
    @Test fun extractsInRequestedOrder() { assertEquals(listOf(4,1,2,3,0),parsePages("5, 2 - 4, 1",5)) }
    @Test fun rejectsInvalidOrDuplicatePages() {
        listOf("0","6","3-1","1,1","1-3,2","1,","-1","a","1--2","999999999999").forEach { value ->
            assertTrue("Expected rejection: $value",runCatching { parsePages(value,5) }.isFailure)
        }
    }
    @Test fun mapsAllRotationsAndCropOffsets() {
        val expected=listOf(
            floatArrayOf(10f,20f,610f,820f),
            floatArrayOf(610f,20f,10f,820f),
            floatArrayOf(610f,820f,10f,20f),
            floatArrayOf(10f,820f,610f,20f)
        )
        listOf(0,90,180,270).forEachIndexed { i,r ->
            val m=pageTransform(r,10f,20f,600f,800f)
            val w=if(r%180==0) 600f else 800f; val h=if(r%180==0) 800f else 600f
            val start=floatArrayOf(m[4],m[5]); val end=floatArrayOf(m[0]*w+m[2]*h+m[4],m[1]*w+m[3]*h+m[5])
            assertArrayEquals(expected[i].sliceArray(0..1),start,.001f)
            assertArrayEquals(expected[i].sliceArray(2..3),end,.001f)
        }
    }
    @Test fun rotationNormalizesNegativeAndFullTurns() { assertArrayEquals(pageTransform(270,0f,0f,600f,800f),pageTransform(-90,0f,0f,600f,800f),0f); assertArrayEquals(pageTransform(0,0f,0f,600f,800f),pageTransform(360,0f,0f,600f,800f),0f) }
}
