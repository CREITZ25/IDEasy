package com.devonfw.tools.ide.version;

import java.util.List;
import java.util.Objects;

import com.devonfw.tools.ide.cli.CliException;
import com.devonfw.tools.ide.log.IdeLogger;
import com.devonfw.tools.ide.tool.ToolCommandlet;

/**
 * Data-type to represent a {@link VersionIdentifier} in a structured way and allowing {@link #compareVersion(VersionIdentifier) comparison} of
 * {@link VersionIdentifier}s.
 */
public final class VersionIdentifier implements VersionObject<VersionIdentifier>, GenericVersionRange {

  /** {@link VersionIdentifier} "*" that will resolve to the latest stable version. */
  public static final VersionIdentifier LATEST = VersionIdentifier.of("*");

  /** {@link VersionIdentifier} "*!" that will resolve to the latest snapshot. */
  public static final VersionIdentifier LATEST_UNSTABLE = VersionIdentifier.of("*!");

  private final VersionSegment start;

  private final VersionLetters developmentPhase;

  private final boolean valid;

  private VersionIdentifier(VersionSegment start) {

    super();
    Objects.requireNonNull(start);
    this.start = start;
    boolean isValid = this.start.getSeparator().isEmpty() && this.start.getLettersString().isEmpty();
    boolean hasPositiveNumber = false;
    VersionLetters dev = VersionLetters.EMPTY;
    VersionSegment segment = this.start;
    while (segment != null) {
      if (!segment.isValid()) {
        isValid = false;
      } else if (segment.getNumber() > 0) {
        hasPositiveNumber = true;
      }
      VersionLetters segmentLetters = segment.getLetters();
      if (segmentLetters.isDevelopmentPhase()) {
        if (dev.isEmpty()) {
          dev = segmentLetters;
        } else {
          dev = VersionLetters.UNDEFINED;
          isValid = false;
        }
      }
      segment = segment.getNextOrNull();
    }
    this.developmentPhase = dev;
    this.valid = isValid && hasPositiveNumber;
  }

  /**
   * Resolves a version pattern against a list of available versions.
   *
   * @param version the version pattern to resolve
   * @param versions the
   *     {@link com.devonfw.tools.ide.tool.repository.ToolRepository#getSortedVersions(String, String, ToolCommandlet) available versions, sorted in descending
   *     order}.
   * @param logger the {@link IdeLogger}.
   * @return the resolved version
   */
  public static VersionIdentifier resolveVersionPattern(GenericVersionRange version, List<VersionIdentifier> versions, IdeLogger logger) {
    if (version == null) {
      version = LATEST;
    }
    if (!version.isPattern()) {
      return (VersionIdentifier) version;
    }
    for (VersionIdentifier vi : versions) {
      if (version.contains(vi)) {
        logger.debug("Resolved version pattern {} to version {}", version, vi);
        return vi;
      }
    }
    throw new CliException(
        "Could not find any version matching '" + version + "' - there are " + versions.size() + " version(s) available but none matched!");
  }

  /**
   * @return the first {@link VersionSegment} of this {@link VersionIdentifier}. To get other segments use {@link VersionSegment#getNextOrEmpty()} or
   *     {@link VersionSegment#getNextOrNull()}.
   */
  public VersionSegment getStart() {

    return this.start;
  }

  /**
   * A valid {@link VersionIdentifier} has to meet the following requirements:
   * <ul>
   * <li>All {@link VersionSegment segments} themselves are {@link VersionSegment#isValid() valid}.</li>
   * <li>The {@link #getStart() start} {@link VersionSegment segment} shall have an {@link String#isEmpty() empty}
   * {@link VersionSegment#getSeparator() separator} (e.g. ".1.0" or "-1-2" are not considered valid).</li>
   * <li>The {@link #getStart() start} {@link VersionSegment segment} shall have an {@link String#isEmpty() empty}
   * {@link VersionSegment#getLettersString() letter-sequence} (e.g. "RC1" or "beta" are not considered valid).</li>
   * <li>Have at least one {@link VersionSegment segment} with a positive {@link VersionSegment#getNumber() number}
   * (e.g. "0.0.0" or "0.alpha" are not considered valid).</li>
   * <li>Have at max one {@link VersionSegment segment} with a {@link VersionSegment#getPhase() phase} that is a real
   * {@link VersionPhase#isDevelopmentPhase() development phase} (e.g. "1.alpha1.beta2" or "1.0.rc1-milestone2" are not
   * considered valid).</li>
   * <li>It is NOT a {@link #isPattern() pattern}.</li>
   * </ul>
   */
  @Override
  public boolean isValid() {

    return this.valid;
  }

  @Override
  public boolean isPattern() {

    VersionSegment segment = this.start;
    while (segment != null) {
      if (segment.isPattern()) {
        return true;
      }
      segment = segment.getNextOrNull();
    }
    return false;
  }

  /**
   * @return the {@link VersionLetters#isDevelopmentPhase() development phase} of this {@link VersionIdentifier}. Will be {@link VersionLetters#EMPTY} if no
   *     development phase is specified in any {@link VersionSegment} and will be {@link VersionLetters#UNDEFINED} if more than one
   *     {@link VersionLetters#isDevelopmentPhase() development phase} is specified (e.g. "1.0-alpha1.rc2").
   */
  public VersionLetters getDevelopmentPhase() {

    return this.developmentPhase;
  }

  @Override
  public VersionComparisonResult compareVersion(VersionIdentifier other) {

    if (other == null) {
      return VersionComparisonResult.GREATER_UNSAFE;
    }
    VersionSegment thisSegment = this.start;
    VersionSegment otherSegment = other.start;
    VersionComparisonResult result = null;
    boolean unsafe = false;
    boolean todo = true;
    do {
      result = thisSegment.compareVersion(otherSegment);
      if (result.isEqual()) {
        if (thisSegment.isEmpty() && otherSegment.isEmpty()) {
          todo = false;
        } else if (result.isUnsafe()) {
          unsafe = true;
        }
      } else {
        todo = false;
      }
      thisSegment = thisSegment.getNextOrEmpty();
      otherSegment = otherSegment.getNextOrEmpty();
    } while (todo);
    if (unsafe) {
      return result.withUnsafe();
    }
    return result;
  }

  /**
   * @param other the {@link VersionIdentifier} to be matched.
   * @return {@code true} if this {@link VersionIdentifier} is equal to the given {@link VersionIdentifier} or this {@link VersionIdentifier} is a pattern
   *     version (e.g. "17*" or "17.*") and the given {@link VersionIdentifier} matches to that pattern.
   */
  public boolean matches(VersionIdentifier other) {

    if (other == null) {
      return false;
    }
    VersionSegment thisSegment = this.start;
    VersionSegment otherSegment = other.start;
    while (true) {
      VersionMatchResult matchResult = thisSegment.matches(otherSegment);
      if (matchResult == VersionMatchResult.MATCH) {
        return true;
      } else if (matchResult == VersionMatchResult.MISMATCH) {
        return false;
      }
      thisSegment = thisSegment.getNextOrEmpty();
      otherSegment = otherSegment.getNextOrEmpty();
    }
  }

  @Override
  public VersionIdentifier getMin() {

    return this;
  }

  @Override
  public VersionIdentifier getMax() {

    return this;
  }

  @Override
  public boolean contains(VersionIdentifier version) {

    return matches(version);
  }

  @Override
  public int hashCode() {

    VersionSegment segment = this.start;
    int hash = 1;
    while (segment != null) {
      hash = hash * 31 + segment.hashCode();
      segment = segment.getNextOrNull();
    }
    return hash;
  }

  @Override
  public boolean equals(Object obj) {

    if (obj == this) {
      return true;
    } else if (!(obj instanceof VersionIdentifier)) {
      return false;
    }
    VersionIdentifier other = (VersionIdentifier) obj;
    return Objects.equals(this.start, other.start);
  }

  @Override
  public String toString() {

    StringBuilder sb = new StringBuilder();
    VersionSegment segment = this.start;
    while (segment != null) {
      sb.append(segment.toString());
      segment = segment.getNextOrNull();
    }
    return sb.toString();
  }

  /**
   * @param version the {@link #toString() string representation} of the {@link VersionIdentifier} to parse.
   * @return the parsed {@link VersionIdentifier}.
   */
  public static VersionIdentifier of(String version) {

    if (version == null) {
      return null;
    } else if (version.equals("latest")) {
      return VersionIdentifier.LATEST;
    }
    VersionSegment startSegment = VersionSegment.of(version);
    if (startSegment == null) {
      return null;
    }
    return new VersionIdentifier(startSegment);
  }

}
