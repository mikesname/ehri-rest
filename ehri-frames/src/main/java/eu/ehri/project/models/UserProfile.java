package eu.ehri.project.models;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

import eu.ehri.project.models.annotations.EntityEnumType;
import eu.ehri.project.models.annotations.Fetch;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.base.Annotator;

@EntityEnumType(EntityEnumTypes.USER_PROFILE)
public interface UserProfile extends VertexFrame, Accessor, AccessibleEntity,
        Annotator, Actioner {

    @Fetch
    @Adjacency(label = Group.BELONGS_TO)
    public Iterable<Group> getGroups();

    @Property("name")
    public String getName();

    @Property("name")
    public void setName(String name);
}
