package com.borkdominik.big.glsp.uml.converter.report;

public final class ProfileMetadata {
    private final String profilePath;
    private final String profileRootId;
    private final String epackageId;
    private final String selectionStrategy;
    private final String epackageNsUri;
    private final String epackageVersion;
    private final String epackageDate;
    private final Integer candidateCount;

    public ProfileMetadata(
        String profilePath,
        String profileRootId,
        String epackageId,
        String selectionStrategy,
        String epackageNsUri,
        String epackageVersion,
        String epackageDate,
        Integer candidateCount
    ) {
        this.profilePath = profilePath;
        this.profileRootId = profileRootId;
        this.epackageId = epackageId;
        this.selectionStrategy = selectionStrategy;
        this.epackageNsUri = epackageNsUri;
        this.epackageVersion = epackageVersion;
        this.epackageDate = epackageDate;
        this.candidateCount = candidateCount;
    }

    public static ProfileMetadata empty() {
        return new ProfileMetadata(null, null, null, null, null, null, null, null);
    }

    public String getProfilePath() {
        return profilePath;
    }

    public String getProfileRootId() {
        return profileRootId;
    }

    public String getEpackageId() {
        return epackageId;
    }

    public String getSelectionStrategy() {
        return selectionStrategy;
    }

    public String getEpackageNsUri() {
        return epackageNsUri;
    }

    public String getEpackageVersion() {
        return epackageVersion;
    }

    public String getEpackageDate() {
        return epackageDate;
    }

    public Integer getCandidateCount() {
        return candidateCount;
    }
}
