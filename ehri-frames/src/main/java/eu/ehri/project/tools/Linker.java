package eu.ehri.project.tools;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.DocumentDescription;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.UndeterminedRelationship;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistence.ActionManager;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.utils.Slugify;
import eu.ehri.project.views.impl.CrudViews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;


/**
 * Utility class for performing operations on the database
 * related to the creation and validation of links between
 * items.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class Linker {

    private static final Logger logger = LoggerFactory.getLogger(Linker.class);

    private static final String LINK_TYPE = "associative";

    private final FramedGraph<?> graph;

    public Linker(FramedGraph<?> graph) {
        this.graph = graph;
    }

    /**
     * Populate a pre-created vocabulary with concepts created based on
     * access points for all collections within a repository, then link
     * those concepts to the relevant documentary units.
     * <p/>
     * One creation event will be generated for the newly-created concepts
     * (with the vocabulary as the scope) and another for the newly-created
     * links. Currently events will still be created if no concepts/links
     * are made.
     * <p/>
     * It should be advised that this function is not idempotent and
     * running it twice will generate concepts/links twice.
     * <p/>
     * NB. One could argue this function does too much...
     *
     * @param repository       the repository
     * @param vocabulary       an existing (presumably empty) vocabulary
     * @param user             the user to whom to attribute the operation
     * @param languageCode     the language to use for the concept descriptions
     * @param accessPointTypes the access point types to include. If an empty
     *                         list is provided <b>all</b> access points will
     *                         be created as concepts
     * @param logMessage       a log message to use for the creation actions
     * @param tolerant         proceed even if there are integrity errors caused
     *                         by two distinct concept names slugifying to the
     *                         same string
     * @return the number of new links created
     * @throws ItemNotFound
     * @throws ValidationError
     * @throws PermissionDenied
     */
    public long createAndLinkRepositoryVocabulary(
            Repository repository,
            Vocabulary vocabulary,
            UserProfile user,
            String languageCode,
            List<String> accessPointTypes,
            Optional<String> logMessage,
            boolean tolerant)
            throws ItemNotFound, ValidationError, PermissionDenied {

        // First, build a map of access point names to (null) concepts
        Map<String, String> conceptIdentifierNames = Maps.newHashMap();
        Map<String, Optional<Concept>> identifierConcept = Maps.newHashMap();

        for (DocumentaryUnit doc : repository.getAllCollections()) {
            for (DocumentDescription description : doc.getDocumentDescriptions()) {
                for (UndeterminedRelationship relationship : description.getUndeterminedRelationships()) {
                    if (accessPointTypes.isEmpty() || accessPointTypes
                            .contains(relationship.getRelationshipType())) {
                        String trimmedName = relationship.getName().trim();
                        String identifier = getIdentifier(relationship);
                        String prior = conceptIdentifierNames.get(identifier);
                        if (identifier.isEmpty() || trimmedName.isEmpty()) {
                            logger.warn("Ignoring empty access point name");
                        } else if (prior != null && !prior.equals(trimmedName)) {
                            logger.warn("Concept name/slug collision: '{}' -> '{}'", trimmedName,
                                    prior);
                        } else {
                            conceptIdentifierNames.put(identifier, trimmedName);
                            identifierConcept.put(identifier, Optional.<Concept>absent());
                        }
                    }
                }
            }
        }

        // Abort if we've got no concepts - this avoids creating
        // an event unnecessarily...
        if (identifierConcept.isEmpty()) {
            return 0L;
        }

        // Now create concepts for all the names
        ActionManager actionManager = new ActionManager(graph);
        ActionManager.EventContext conceptEvent = actionManager
                .setScope(vocabulary)
                .logEvent(user, EventTypes.creation, logMessage);
        CrudViews<Concept> conceptMaker = new CrudViews<Concept>(graph, Concept.class, vocabulary);

        for (Map.Entry<String, String> idName : conceptIdentifierNames.entrySet()) {
            String identifier = idName.getKey();
            String name = idName.getValue();
            Bundle conceptBundle = Bundle.Builder.withClass(EntityClass.CVOC_CONCEPT)
                    .addDataValue(Ontology.IDENTIFIER_KEY, identifier)
                    .addRelation(Ontology.DESCRIPTION_FOR_ENTITY, Bundle.Builder
                            .withClass(EntityClass.CVOC_CONCEPT_DESCRIPTION)
                            .addDataValue(Ontology.LANGUAGE_OF_DESCRIPTION, languageCode)
                            .addDataValue(Ontology.NAME_KEY, name)
                            .build())
                    .build();

            try {
                Concept concept = conceptMaker.create(conceptBundle, user);
                concept.setVocabulary(vocabulary);
                identifierConcept.put(identifier, Optional.fromNullable(concept));
                conceptEvent.addSubjects(concept);
            } catch (ValidationError validationError) {
                // If this happens it is most likely because two access points
                // slugified to the same name due to the removal of diacritics
                // etc. The createOrUpdate operation currently doesn't seem to
                // work in the same transaction (possibly due to the graph index
                // not being flushed), so for the moment we're just going to log
                // the error and continue.
                logger.warn("Id/name collision error: '{}' -> '{}' ('{}')", identifier, name,
                        conceptIdentifierNames.get(identifier));
                logger.error("Link integrity error: ", validationError);
                if (!tolerant) {
                    throw validationError;
                }
            }
        }

        // Now link the concepts with elements having the access point from
        // which the concept originally derived.
        ActionManager.EventContext linkEvent = actionManager
                .logEvent(user, EventTypes.creation, logMessage);
        CrudViews<Link> linkMaker = new CrudViews<Link>(graph, Link.class);

        long linkCount = 0L;
        for (DocumentaryUnit doc : repository.getAllCollections()) {
            for (DocumentDescription description : doc.getDocumentDescriptions()) {
                for (UndeterminedRelationship relationship : description.getUndeterminedRelationships()) {
                    if (accessPointTypes.isEmpty() || accessPointTypes
                            .contains(relationship.getRelationshipType())) {

                        String identifier = getIdentifier(relationship);
                        Optional<Concept> conceptOpt = identifierConcept.get(identifier);
                        if (conceptOpt.isPresent()) {
                            Concept concept = conceptOpt.get();
                            Bundle linkBundle = Bundle.Builder.withClass(EntityClass.LINK)
                                    .addDataValue(Ontology.LINK_HAS_TYPE, LINK_TYPE)
                                    .build();
                            Link link = linkMaker.create(linkBundle, user);
                            link.addLinkTarget(doc);
                            link.addLinkTarget(concept);
                            link.addLinkBody(relationship);
                            linkEvent.addSubjects(link);
                            linkCount++;
                        }
                    }
                }
            }
        }

        return linkCount;
    }

    private String getIdentifier(UndeterminedRelationship relationship) {
        return Slugify.slugify(relationship.getName().trim())
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}