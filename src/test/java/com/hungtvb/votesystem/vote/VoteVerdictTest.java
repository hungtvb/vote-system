package com.hungtvb.votesystem.vote;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoteVerdictTest {

    @Test
    void returnsUndecidedWhenThereAreNoVotes() {
        assertThat(VoteVerdict.from(0, 0, 70)).isEqualTo(VoteVerdict.UNDECIDED);
    }

    @Test
    void remainsUndecidedAtFiftyFifty() {
        assertThat(VoteVerdict.from(50, 50, 70)).isEqualTo(VoteVerdict.UNDECIDED);
    }

    @Test
    void doesNotRoundSixtyNinePointFiveUpToVerdict() {
        assertThat(VoteVerdict.from(139, 61, 70)).isEqualTo(VoteVerdict.UNDECIDED);
    }

    @Test
    void reachesUpVerdictAtExactThreshold() {
        assertThat(VoteVerdict.from(70, 30, 70)).isEqualTo(VoteVerdict.UP);
    }

    @Test
    void reachesDownVerdictAtExactThreshold() {
        assertThat(VoteVerdict.from(30, 70, 70)).isEqualTo(VoteVerdict.DOWN);
    }

    @Test
    void reachesVerdictAtOneHundredPercent() {
        assertThat(VoteVerdict.from(1, 0, 70)).isEqualTo(VoteVerdict.UP);
        assertThat(VoteVerdict.from(0, 1, 70)).isEqualTo(VoteVerdict.DOWN);
    }
}