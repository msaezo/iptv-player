package es.msaezo.iptv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SportsTest {
    private val now = 1_800_000_000_000L // 2027-01-15T08:00Z
    private val liga = SportsRepository.ALL.first { it.id == "laliga" }
    private val f1 = SportsRepository.ALL.first { it.id == "f1" }
    private val atp = SportsRepository.ALL.first { it.id == "atp" }

    @Test fun teamFixture() {
        val json = """{"events":[
          {"name":"Barcelona at Real Madrid","date":"2027-01-15T20:00Z","status":{"type":{"state":"pre"}},
           "competitions":[{"date":"2027-01-15T20:00Z","competitors":[
              {"homeAway":"home","team":{"displayName":"Real Madrid"}},{"homeAway":"away","team":{"displayName":"Barcelona"}}]}]},
          {"name":"Old game","date":"2027-01-14T20:00Z","status":{"type":{"state":"post"}},
           "competitions":[{"date":"2027-01-14T20:00Z","competitors":[{"team":{"displayName":"A"}},{"team":{"displayName":"B"}}]}]}
        ]}"""
        val out = SportsRepository.parse(liga, JSONObject(json), now)
        assertEquals(listOf("Real Madrid vs Barcelona"), out.map { it.title })
        assertEquals(false, out[0].live)
    }

    @Test fun raceSessions() {
        val json = """{"events":[{"name":"Spanish Grand Prix","date":"2027-01-15T10:00Z","competitions":[
          {"date":"2027-01-15T10:30Z","type":{"abbreviation":"FP1"},"status":{"type":{"state":"in","shortDetail":"Lap 3"}},
           "competitors":[{"athlete":{"displayName":"X"}},{"athlete":{"displayName":"Y"}},{"athlete":{"displayName":"Z"}}]},
          {"date":"2027-01-16T14:00Z","type":{"abbreviation":"Qual"},"status":{"type":{"state":"pre"}},
           "competitors":[{"athlete":{"displayName":"X"}},{"athlete":{"displayName":"Y"}},{"athlete":{"displayName":"Z"}}]}]}]}"""
        val out = SportsRepository.parse(f1, JSONObject(json), now)
        assertEquals(listOf("Spanish Grand Prix · FP1", "Spanish Grand Prix · Qual"), out.map { it.title })
        assertEquals(true, out[0].live)
    }

    @Test fun tennisMatchesInsideGroupings() {
        val json = """{"events":[{"name":"ATP Finals","date":"2027-01-15T10:00Z","groupings":[{"competitions":[
          {"date":"2027-01-15T13:00Z","status":{"type":{"state":"pre"}},
           "competitors":[{"athlete":{"displayName":"Carlos Alcaraz"}},{"athlete":{"displayName":"Jannik Sinner"}}]}]}]}]}"""
        val out = SportsRepository.parse(atp, JSONObject(json), now)
        assertEquals(listOf("Carlos Alcaraz vs Jannik Sinner"), out.map { it.title })
    }

    @Test fun unknownShapeGivesNothing() {
        assertEquals(0, SportsRepository.parse(liga, JSONObject("""{"something":"else"}"""), now).size)
    }
}
