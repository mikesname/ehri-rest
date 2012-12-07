package eu.ehri.project.models;

import com.tinkerpop.frames.VertexFrame;

import eu.ehri.project.definitions.Entities;
import eu.ehri.project.models.idgen.AccessibleEntityIdGenerator;
import eu.ehri.project.models.idgen.DocumentaryUnitIdGenerator;
import eu.ehri.project.models.idgen.GenericIdGenerator;
import eu.ehri.project.models.idgen.IdGenerator;

/**
 * Mapping of Entity type names to Frames interfaces classes and
 * their associated IdGenerator functors.
 * 
 * @author michaelb
 *
 */
public enum EntityEnumTypes {

    // @formatter:off
    DOCUMENTARY_UNIT(Entities.DOCUMENTARY_UNIT, "c", DocumentaryUnit.class, DocumentaryUnitIdGenerator.class),
    AGENT(Entities.AGENT, "r", Agent.class, AccessibleEntityIdGenerator.class),
    AUTHORITY(Entities.AUTHORITY, "a", Authority.class, AccessibleEntityIdGenerator.class),
    GROUP(Entities.GROUP, "g", Group.class, AccessibleEntityIdGenerator.class),
    USER_PROFILE(Entities.USER_PROFILE, "u", UserProfile.class, AccessibleEntityIdGenerator.class),
    
    // Generic entities.
    DOCUMENT_DESCRIPTION(Entities.DOCUMENT_DESCRIPTION, "dd", DocumentDescription.class),
    AGENT_DESCRIPTION(Entities.AGENT_DESCRIPTION, "rd", AgentDescription.class),
    AUTHORITY_DESCRIPTION(Entities.AUTHORITY_DESCRIPTION, "ad", AuthorityDescription.class),
    DATE_PERIOD(Entities.DATE_PERIOD, "dp", DatePeriod.class),
    ANNOTATION(Entities.ANNOTATION, "ann", Annotation.class),
    ADDRESS(Entities.ADDRESS, "adr", Address.class),
    ACTION(Entities.ACTION, "act", Action.class),
    IMPORT(Entities.IMPORT, "imp", Import.class),
    PROPERTY(Entities.PROPERTY, "p", Property.class),
    PERMISSION(Entities.PERMISSION, "pm", Permission.class),
    PERMISSION_GRANT(Entities.PERMISSION_GRANT, "pmg", PermissionGrant.class),
    CONTENT_TYPE(Entities.CONTENT_TYPE, "ct", ContentType.class),
    REVISION(Entities.REVISION, "rv", Revision.class);
    // @formatter:on

    // Accessors.

    /**
     * Get the string name of this EntityType.
     * 
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Get the abbreviation code of this entityType.
     * 
     * @return
     */
    public String getAbbreviation() {
        return abbr;
    }

    /**
     * Get the interface to which this EntityType refers.
     * 
     * @return
     */
    public Class<? extends VertexFrame> getEntityClass() {
        return cls;
    }

    /**
     * Get the ID generator class for this entityType.
     * 
     * @return
     */
    public Class<? extends IdGenerator> getIdgen() {
        return idgen;
    }

    public static EntityEnumTypes withName(String name) {
        for (EntityEnumTypes et : EntityEnumTypes.values()) {
            if (et.getName().equals(name))
                return et;
        }
        throw new IllegalArgumentException("Invalid entity type: " + name);
    }

    private final String name;
    private final String abbr;
    private final Class<? extends VertexFrame> cls;
    private final Class<? extends IdGenerator> idgen;

    private EntityEnumTypes(String name, String abbr,
            Class<? extends VertexFrame> cls, Class<? extends IdGenerator> idgen) {
        this.name = name;
        this.abbr = abbr;
        this.cls = cls;
        this.idgen = idgen;
    }

    /**
     * Short constructor.
     * 
     * @param name
     * @param abbr
     * @param cls
     */
    private EntityEnumTypes(String name, String abbr,
            Class<? extends VertexFrame> cls) {
        this(name, abbr, cls, GenericIdGenerator.class);
    }

    @Override
    public String toString() {
        return name;
    }
}
