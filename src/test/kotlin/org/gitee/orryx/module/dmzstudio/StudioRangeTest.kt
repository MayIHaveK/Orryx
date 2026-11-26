package org.gitee.orryx.module.dmzstudio

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.math.*

class StudioRangeTest {
    private fun box(x:Double,z:Double,y:Double=0.0)=StudioRangeCollision.Bounds(x-.2,y,z-.2,x+.2,y+1.8,z+.2)
    @Test fun `shared footprint excludes outside rear and vertically separated targets`() {
        val circle=StudioRange(radius=3.0)
        assertTrue(StudioRangeCollision.intersects(circle.footprint(),0.0,3.0,box(2.0,0.0)))
        assertFalse(StudioRangeCollision.intersects(circle.footprint(),0.0,3.0,box(3.5,0.0)))
        assertFalse(StudioRangeCollision.intersects(circle.footprint(),0.0,3.0,box(0.0,0.0,4.0)))
        val sector=StudioRange(shape="SECTOR",radius=4.0,angle=90.0)
        assertTrue(StudioRangeCollision.intersects(sector.footprint(),0.0,3.0,box(0.0,2.0)))
        assertFalse(StudioRangeCollision.intersects(sector.footprint(),0.0,3.0,box(0.0,-2.0)))
        val west=sector.footprint().map { StudioRange.Point(-it.z,it.x) }
        assertTrue(StudioRangeCollision.intersects(west,0.0,3.0,box(-2.0,0.0)))
        assertFalse(StudioRangeCollision.intersects(west,0.0,3.0,box(2.0,0.0)))
        val rect=StudioRange(shape="RECTANGLE",width=2.0,length=4.0)
        assertTrue(StudioRangeCollision.intersects(rect.footprint(),0.0,3.0,box(.9,2.0)))
        assertFalse(StudioRangeCollision.intersects(rect.footprint(),0.0,3.0,box(1.5,2.0)))
    }
    @Test fun `per cast hit ledgers enforce cadence target caps and independent recasts`() {
        val target=UUID.randomUUID();val other=UUID.randomUUID()
        val once=StudioHitLedger(1,1,0);assertTrue(once.eligible(target,0));once.record(target,0)
        assertFalse(once.eligible(target,20));assertFalse(once.eligible(other,20))
        assertTrue(StudioHitLedger(1,1,0).eligible(target,20))
        val periodic=StudioHitLedger(2,2,5);periodic.record(target,10)
        assertFalse(periodic.eligible(target,14));assertTrue(periodic.eligible(target,15));periodic.record(target,15)
        assertFalse(periodic.eligible(target,30));assertTrue(periodic.eligible(other,30))
    }
    @Test fun `v1 stays readable and v2 rejects unsafe or mismatched windows`() {
        StudioProject.decode("{\"id\":\"old\",\"tracks\":[]}")
        val motion=StudioTrack("motion",StudioTrackKind.ANIMATION,"test:clip",start=0,duration=30)
        val hit=StudioTrack("hit",StudioTrackKind.HITBOX,"impact",start=16,duration=3,range=StudioRange(motion="motion"))
        val project=StudioProject(version=2,id="skill",tracks=listOf(motion,hit));project.validate()
        assertThrows(IllegalArgumentException::class.java){project.copy(version=1).validate()}
        assertThrows(IllegalArgumentException::class.java){project.copy(tracks=listOf(hit)).validate()}
        assertThrows(IllegalArgumentException::class.java){project.copy(tracks=listOf(motion,hit.copy(range=hit.range!!.copy(damage=Double.NaN)))).validate()}
        assertThrows(IllegalArgumentException::class.java){project.copy(tracks=listOf(motion,hit.copy(start=5))).validate()}
    }
}
