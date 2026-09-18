package com.auracommunityact.missiongtamobile.storage

data class ResourceRequirement(
    val requiredDirectories: List<String>,
    val requiredRootFiles: List<String>,
    val ignoredFilesRegex: List<Regex>
)

object GtaVResourceRequirement {
    val requirement = ResourceRequirement(
        requiredDirectories = listOf(
            "Config",
            "Drivers",
            "dxuk-cache",
            "save",
            "update",
            "x64"
        ),
        requiredRootFiles = listOf(
            "common.rpf",
            "x64a.rpf",
            "x64b.rpf",
            "x64c.rpf",
            "x64d.rpf",
            "x64e.rpf",
            "x64f.rpf",
            "x64g.rpf",
            "x64h.rpf",
            "x64i.rpf",
            "x64j.rpf",
            "x64k.rpf",
            "x64l.rpf",
            "x64m.rpf",
            "x64n.rpf",
            "x64o.rpf",
            "x64p.rpf",
            "x64q.rpf",
            "x64r.rpf",
            "x64s.rpf",
            "x64t.rpf",
            "x64u.rpf",
            "x64v.rpf",
            "x64w.rpf"
        ),
        ignoredFilesRegex = listOf(
            Regex(".*\\.log$"),
            Regex(".*\\.bin$")
        )
    )
}

