package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * SpecializedAgents — defined agent types for the TASK and PARALLEL_TASKS tools.
 *
 * Each agent type has a focused system prompt, a tailored toolset, and a turn budget.
 * The main agent delegates to these for parallelizable or specialized work.
 *
 * Agent Types:
 *   - general-purpose: Research, search, multi-step tasks (default)
 *   - explore: Fast codebase exploration, file discovery, architecture understanding
 *   - plan: Software architect — designs implementation strategies
 *   - frontend-styling-expert: CSS, responsive design, UI/UX, animations
 *   - full-stack-developer: Complete web app builder (Next.js, React, Prisma)
 *   - ppt-expert: Presentation design and generation specialist
 */

object SpecializedAgents {

    data class AgentType(
        val id: String,
        val name: String,
        val description: String,
        val systemPromptSuffix: String,
        val maxTurns: Int = 15,
        val toolCategories: Set<String> = setOf("core"), // which StructuredTools categories to include
        val preferredSkills: List<String> = emptyList()    // skills to auto-load
    )

    val AGENT_TYPES = listOf(

        AgentType(
            id = "general-purpose",
            name = "General Purpose",
            description = "Research, search, multi-step tasks, and general problem solving",
            systemPromptSuffix = """You are a general-purpose research agent. You can search the web, read files, and analyze information. Your job is to thoroughly investigate the task given to you and return a well-structured, detailed result.

RULES:
- Be thorough: search multiple sources, cross-reference information.
- Structure your findings clearly with headers and bullet points.
- If you find conflicting information, note the conflict and sources.
- Always cite sources (URLs) when presenting findings.
- Return your final result via ANSWER, then DONE.""",
            maxTurns = 12
        ),

        AgentType(
            id = "explore",
            name = "Codebase Explorer",
            description = "Fast codebase search, file discovery, architecture understanding",
            systemPromptSuffix = """You are a FAST codebase exploration agent. Your job is to find specific files, understand project structure, and answer questions about the codebase.

STRATEGY:
1. Start with LIST_FILES to understand the project structure.
2. Use SEARCH_CODE for natural-language queries ("where is the auth logic?").
3. Use SYMBOL_SEARCH for specific symbol definitions.
4. Use GREP for exact pattern matching.
5. READ_FILE only the files that are relevant to the question.

RULES:
- Be FAST. Don't read files you don't need.
- Focus on the specific question asked — don't explore unrelated areas.
- When you find the answer, return it immediately via ANSWER + DONE.
- For architecture questions, provide a clear summary with file references.""",
            maxTurns = 10
        ),

        AgentType(
            id = "plan",
            name = "Software Architect",
            description = "Designs implementation strategies and technical plans",
            systemPromptSuffix = """You are a senior software architect. Your job is to analyze the codebase and design an implementation plan for the requested feature or change.

OUTPUT FORMAT:
1. **Current State**: Brief summary of the relevant existing code/architecture.
2. **Proposed Changes**: Detailed list of files to create/modify, with specific descriptions.
3. **Data Model Changes** (if any): Schema, migrations, new tables.
4. **API Changes** (if any): New endpoints, modified endpoints.
5. **Risk Assessment**: Potential issues, backward compatibility concerns.
6. **Implementation Order**: Recommended order of changes (dependencies first).
7. **Testing Strategy**: What to test and how.

RULES:
- Explore the codebase FIRST before making any recommendations.
- Be specific: name exact files, functions, and the changes needed.
- Consider edge cases, error handling, and performance implications.
- If there are multiple approaches, compare them and recommend one.
- Return the plan via ANSWER + DONE.""",
            maxTurns = 15,
            toolCategories = setOf("core")
        ),

        AgentType(
            id = "frontend-styling-expert",
            name = "Frontend Styling Expert",
            description = "CSS, responsive design, UI/UX, animations, layout systems",
            systemPromptSuffix = """You are a senior frontend styling expert specializing in CSS, responsive design, UI/UX implementation, animations, and layout systems.

EXPERTISE:
- CSS Grid, Flexbox, and modern layout techniques.
- Responsive design (mobile-first, breakpoints, fluid typography).
- CSS animations, transitions, and keyframe animations.
- Design systems: color palettes, typography scales, spacing systems.
- Component styling patterns (BEM, CSS Modules, Tailwind utilities).
- Accessibility: ARIA attributes, focus management, color contrast.

RULES:
- Always consider mobile/tablet/desktop breakpoints.
- Use CSS custom properties for theming.
- Prefer CSS animations over JavaScript animations.
- Ensure WCAG AA color contrast ratios.
- Test layout at 320px, 768px, 1024px, 1440px.
- Return your styling solution via ANSWER + DONE.""",
            maxTurns = 12,
            preferredSkills = listOf("charts")
        ),

        AgentType(
            id = "full-stack-developer",
            name = "Full-Stack Developer",
            description = "Complete web app builder with Next.js, React, Prisma, Tailwind",
            systemPromptSuffix = """You are a senior full-stack developer. You build complete, production-ready web applications.

STACK:
- Next.js 16 App Router + TypeScript
- Tailwind CSS 4 + shadcn/ui components
- Prisma ORM for database
- API routes in app/api/

WORKFLOW:
1. Understand the requirements fully.
2. Design the data model (Prisma schema).
3. Build API routes first (backend).
4. Build UI components (frontend).
5. Connect frontend to backend.
6. Add error handling and loading states.
7. Test the complete flow.

RULES:
- Every file you create must be complete and runnable — no placeholders.
- Use proper TypeScript types (never `any`).
- Use Server Components by default; Client Components only when needed.
- Follow Next.js App Router conventions.
- Always handle errors gracefully.
- Return a summary of what you built via ANSWER + DONE.""",
            maxTurns = 40, // room for multi-file full-stack builds
            toolCategories = setOf("core", "cloud"),
            preferredSkills = listOf("fullstack-dev")
        ),

        AgentType(
            id = "ppt-expert",
            name = "Presentation Expert",
            description = "Professional slide deck design and content creation",
            systemPromptSuffix = """You are a professional presentation designer. You create compelling, well-structured slide decks.

DESIGN PRINCIPLES:
- One idea per slide. No walls of text.
- Use visuals (charts, diagrams, images) over text whenever possible.
- Consistent design language across all slides.
- Clear visual hierarchy: title > subtitle > body > captions.
- High contrast for readability.

CONTENT RULES:
- Title slide: clear title + subtitle.
- Content slides: 3-7 bullet points max. Use speaker notes.
- Data slides: charts with clear labels and titles.
- Closing slide: summary or call-to-action.
- Each bullet should be a complete thought (not a fragment).

OUTPUT: Return the CREATE_PPTX JSON spec via ANSWER + DONE.""",
            maxTurns = 10,
            toolCategories = setOf("core", "office"),
            preferredSkills = listOf("pptx", "charts")
        ),
    )

    /** Get an agent type by ID. Falls back to general-purpose if not found. */
    fun getById(id: String): AgentType =
        AGENT_TYPES.find { it.id == id.lowercase() } ?: AGENT_TYPES.first()

    /**
     * Run a specialized sub-agent and return its result.
     * This is the enhanced version of CodeAgent.runSubAgent that uses agent types.
     */
    suspend fun runSpecialized(
        context: android.content.Context,
        projectDir: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        task: String,
        agentTypeId: String = "general-purpose",
        worklog: String = ""
    ): String = withContext(Dispatchers.IO) {
        val agentType = getById(agentTypeId)

        // Auto-load preferred skills
        val skillInjection = StringBuilder()
        for (skillId in agentType.preferredSkills) {
            val skill = SkillManager.loadSkill(skillId)
            if (skill != null) {
                skillInjection.append("\n\n── ${skill.name.uppercase()} EXPERTISE ──\n${skill.content}\n")
            }
        }

        val tree = runCatching { ProjectManager.buildTreeString(projectDir) }.getOrDefault("(tree unavailable)")

        val systemPrompt = CodeAgent.systemPrompt() + "\n\n" +
            "YOU ARE A SPECIALIZED SUB-AGENT (${agentType.name.uppercase()}).\n" +
            agentType.systemPromptSuffix +
            skillInjection.toString() +
            "\n\nYou have a limited turn budget of ${agentType.maxTurns}. Be efficient." +
            if (worklog.isNotBlank()) "\n\nSHARED WORKLOG (from other agents):\n$worklog" else ""

        val convo = mutableListOf<Pair<String, String>>()
        convo.add("system" to systemPrompt)
        convo.add("user" to "PROJECT FILE TREE:\n$tree\n\nTASK: $task")

        val collected = StringBuilder()
        var answer = ""
        var turn = 0

        while (turn < agentType.maxTurns) {
            turn++
            val resp = ApiClient.streamAgentResponse(baseUrl, apiKey, model, convo) {}
            val raw = resp.getOrNull() ?: break
            convo.add("assistant" to raw)
            val steps = CodeAgent.parseActions(raw)
            if (steps.isEmpty()) {
                answer = ToolCallParser.stripAll(raw).trim()
                break
            }
            val results = StringBuilder()
            var finished = false
            for (s in steps) {
                when (s.action) {
                    "answer" -> { answer = s.detail; finished = true }
                    "done" -> { finished = true }
                    "task" -> {
                        // Nested sub-agents: run as general-purpose with half the remaining turns
                        val remaining = agentType.maxTurns - turn
                        if (remaining > 2) {
                            val subResult = runSpecialized(
                                context, projectDir, baseUrl, apiKey, model,
                                s.detail, "general-purpose", worklog
                            )
                            results.append("[SUB-TASK RESULT]\n$subResult\n\n")
                        } else {
                            results.append("[SUB-TASK] Not enough turns remaining.\n")
                        }
                    }
                    "plan", "completestep" -> { /* ignore in sub-agent */ }
                    "load_skill" -> {
                        val skill = SkillManager.loadSkill(s.detail)
                        if (skill != null) {
                            convo.add("user" to "[SKILL LOADED: ${skill.name}] ${skill.content.take(2000)}")
                            results.append("Loaded skill: ${skill.name}.\n")
                        } else {
                            results.append("Unknown skill: ${s.detail}. Available: ${SkillManager.getAvailableSkillIds().joinToString(", ")}\n")
                        }
                    }
                    else -> {
                        val r = runCatching { CodeAgent.executeStep(context, projectDir, s) }
                            .getOrElse { "ERROR: ${it.message}" }
                        results.append("[${s.action.uppercase()} ${s.path}]\n${CodeAgent.truncateOutput(r, headLines = 30, tailLines = 20, minOutputLines = 40)}\n\n")
                    }
                }
            }
            if (finished) break
            convo.add("user" to (results.toString().ifBlank { "(no output) Continue or finish with ANSWER then DONE." }))
            if (results.isNotBlank()) collected.append(results)
        }

        val result = when {
            answer.isNotBlank() -> "[$agentType] $answer"
            collected.isNotBlank() -> "[$agentType] ${collected.toString().take(3000)}"
            else -> "[$agentType] Finished with no result."
        }

        // Always write worklog when a sub-agent actually ran (model may skip WORKLOG tool).
        Worklog.append(
            projectDir,
            "Completed task: ${task.take(120)}${if (task.length > 120) "…" else ""} (${turn} turns)" +
                if (answer.isNotBlank()) " — ${answer.take(200)}" else "",
            tag = agentType.name
        )

        result
    }

    /**
     * Run multiple specialized agents IN PARALLEL and return all results.
     */
    suspend fun runParallel(
        context: android.content.Context,
        projectDir: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        tasks: List<Triple<String, String, String>>, // (description, agentType, worklog)
    ): List<String> = withContext(Dispatchers.IO) {
        coroutineScope {
            tasks.map { (desc, agentType, wl) ->
                async {
                    runSpecialized(context, projectDir, baseUrl, apiKey, model, desc, agentType, wl)
                }
            }.awaitAll()
        }
    }

    /** Get all available agent type IDs. */
    fun getAvailableTypeIds(): List<String> = AGENT_TYPES.map { it.id }

    /** Get all available skill IDs. */
    fun getAvailableSkillIds(): List<String> = SkillManager.getAvailableSkillIds()

    /** Append an entry to the shared worklog file (delegates to [Worklog]). */
    fun appendWorklog(projectDir: String, entry: String) {
        Worklog.append(projectDir, entry, tag = "Agent")
    }

    /** Read the current worklog. */
    fun readWorklog(projectDir: String): String = Worklog.read(projectDir)
}

// Extension needed by SpecializedAgents
fun SkillManager.getAvailableSkillIds(): List<String> = listOf(
    "pdf", "docx", "xlsx", "pptx", "charts", "fullstack-dev",
    "image-generation", "web-search", "web-reader"
)