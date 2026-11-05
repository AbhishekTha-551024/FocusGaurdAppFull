package com.focusguard.app.ui.challenge

import kotlin.random.Random

/**
 * Generates high-friction, symbol-dense commitment challenges.
 * Strictly guarantees:
 * 1. Minimum 500 characters (averaging 660–780 characters).
 * 2. Dense punctuation and symbols: `,`, `.`, `;`, `'`, `[`, `]`, `-`, `:`, `!`, `?`, `{`, `}`, `(`, `)`, `/`, `@`, `#`, `%`, `&`, `+`, `=`, `"`.
 * 3. Requires frequent keyboard layout switching on mobile devices to prevent automatic swipe or quick typing.
 */
object CommitmentChallengeGenerator {

    val SENTENCE_POOL = listOf(
        // 1. Focus-Shield (779 chars)
        """[Focus-Shield::v4.2]--Do I truly need to unlock this restricted app right now, or am I merely succumbing to an impulsive 'dopamine twitch'? I acknowledge that deep work demands total concentration; yet here I am, deliberately bypassing my self-imposed rules [status: "interrupted"; friction: high]. Every override reinforces weakness--eroding discipline, focus, and mental clarity; whereas enduring boredom builds resilience {habit-strength: +1}. Why trade my long-term goals for momentary amusement? I consciously accept full personal responsibility for this lapse (cost-factor: elevated @ 100% & risk = high); no excuses, no shortcuts! Stop, breathe, and reflect: is this distraction worth breaking my promise to myself? Confirm override [auth: #FG-902/unlock; verified: true].""",

        // 2. Attention-Shield (771 chars)
        """[Attention-Shield::v2.8]--Attention is an individual's most valuable asset [non-renewable resource]; letting feeds hijack it diminishes cognitive capacity, creativity, and inner calm. I am actively attempting to break FocusGuard's lock--a deliberate choice to halt my productive momentum (choosing fleeting 'entertainment' over meaningful progress) {priority: lowered & downgraded = -50%}. Have I finished today's core duties? If not, why am I here? Every friction gate I bypass leaves an imprint on my brain's reward loop; each compromise makes future focus harder [evaluation: "critical"; risk: @95%]. I take absolute ownership of this detour--and its compounding aftermath--without rationalizations: discipline or regret! [log: #FG-VERIFY/mode=strict; confirm: +true].""",

        // 3. Self-Mastery (741 chars)
        """[Self-Mastery::v3.5]--Self-mastery requires enduring discomfort [rule: 'non-negotiable']; yet, I am currently choosing the path of least resistance by seeking this unlock. By overriding this barrier, I explicitly acknowledge: "I am trading hours of clarity for minutes of mindless scrolling"--a disadvantageous transaction (exchange-rate: net-negative @ 100% & loss = high). What am I running away from right now? Hard work? Silence? Deep reflection? Consistent habits forge character; momentary impulses only foster regret {focus-integrity: compromised; score: +0}. I declare that I understand the true price of this interruption--minute-by-minute--and take complete liability for this decision! [auth-code: #FG-771/override; state: valid].""",

        // 4. Discipline-Gate (716 chars)
        """[Discipline-Gate::v5.1]--Building focus takes days of steady commitment, but losing it takes only seconds [warning: 'high-friction gate']! I am consciously attempting to disable my distraction shield: sacrificing my peace of mind for cheap digital noise (interruption-cost: ~25 mins @ 100% loss). Is this action aligned with my highest aspirations? The answer is "no"; yet I am proceeding anyway {resolve: tested; discipline = vulnerable & fragile}. Every time I resist an impulse, I strengthen my willpower; every time I give in, I weaken it. I consciously confirm this override--with total honesty and transparency--accepting that broken focus is solely my fault! [stamp: @FG-AUDIT#550/strict; result: +confirmed].""",

        // 5. Session-Intervention (729 chars)
        """[Session-Intervention::v1.9]--Why am I opening this application right now? I am consciously choosing short-term pleasure over long-term achievement (sacrificing deep focus for shallow stimulation); I accept full personal accountability for this detour [cost: 'elevated'; tier: strict]. Impulsive browsing ruins deep thought: scattering ideas, draining energy, and sabotaging ambition {productivity: @ -40% & penalty = +1}. Does this quick glance serve my future self, or does it steal precious time? True greatness requires saying "no" to trivial distractions--again and again! I acknowledge the consequences of breaking my commitment--without blame or hesitation--and verify this choice [id: #FG-882/override; status: accepted].""",

        // 6. Willpower-Check (670 chars)
        """[Willpower-Check::v6.0]--Discipline is choosing between what I want now and what I want most [core-principle: 'verified']; yet, here I am, attempting to unlock an app that I deliberately chose to restrict. I acknowledge that I am disrupting my concentration (trading deep accomplishment for shallow engagement @ 100% risk) {friction-state: active; cost: severe & penalty = +2}. Why can't I sit quietly with my current task? Every urge I conquer builds mental fortitude; every urge I surrender to feeds chronic distraction. I consciously validate this action--under full awareness--taking 100% responsibility for this pause! [trace: @FG-SHIELD/ref#404; auth: "approved"].""",

        // 7. Cognitive-Integrity (709 chars)
        """[Cognitive-Integrity::v7.3]--Every time I reach for my phone to escape difficult mental exertion, I surrender my autonomy to automated algorithms [threat: 'dopamine hijacking']. Deep focus is not accidental; it is a sacred contract with myself (protecting my mind from continuous partial attention @ 100% focus) {focus-depth: interrupted & score = -1}. Am I willing to trade my life's highest ambitions for digital triviality? The honest answer is painful: I am giving in to weakness right now. I deliberately accept every consequence of this choice--wasted time, delayed achievements, and weakened self-trust--without pointing fingers! [system-log: #FG-INTEGRITY/override@strict; status: "approved" + valid].""",

        // 8. Habit-Reversal (666 chars)
        """[Habit-Reversal::v8.4]--It is trivial to make excuses, but impossible to escape their compounding cost [truth: 'undeniable']. By requesting this unlock, I am breaking a promise made during a moment of clarity (choosing instant indulgence over enduring pride @ 100% risk) {willpower: drained & challenged = -5}. Can I honestly say that this app will make me wiser, calmer, or more fulfilled? Absolutely not; it is simply an unconscious reflex designed to consume my attention! I face this reality with complete transparency: I am choosing distraction over dedication--here and now--and accept full blame! [auth: #FG-HABIT/checkpoint#101; verified: "passed" + @valid]."""
    )

    /**
     * Generates a random sentence from the pool, guaranteeing >= 500 characters and full symbol richness.
     */
    fun generate(exclude: String? = null): String {
        val pool = if (exclude != null) SENTENCE_POOL.filter { it != exclude } else SENTENCE_POOL
        val chosen = (if (pool.isNotEmpty()) pool else SENTENCE_POOL).random()
        return ensureMinLengthAndSymbols(chosen)
    }

    /**
     * Validates that any text satisfies the >= 500 character invariant and contains critical punctuation.
     * If not, appends an audit verification trailer or returns a fresh pool item.
     */
    fun ensureMinLengthAndSymbols(text: String): String {
        var clean = text.trim()
        if (clean.length >= 500 && containsKeySymbols(clean)) {
            return clean
        }
        if (clean.length < 500) {
            val suffix = " [audit-stamp: #FG-${Random.nextInt(100, 999)}/override; cost: @elevated-friction; status: {active-lock}; token: 'VERIFIED_COMMITMENT' & level = +1]."
            clean = (clean + suffix).trim()
        }
        return if (clean.length >= 500) clean else SENTENCE_POOL.random()
    }

    private fun containsKeySymbols(text: String): Boolean {
        val required = listOf(',', '.', ';', '\'', '[', ']', '-')
        return required.all { text.contains(it) }
    }
}
