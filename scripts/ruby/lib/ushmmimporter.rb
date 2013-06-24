#
# Importer for a USHMM Solr-to-EAD dump.
#
# The directory structure is a bit ad-hoc at the moment,
# with EAD XML files residing in a directory named for
# their immediate parent (or at the top level.)
#
# This is basically a hack.
#

require "#{File.dirname(__FILE__)}/ehri"

# Number of files to import before committing the tx
COMMIT_MAX = 2500


module Ehri
  module UshmmImporter

    include Ehri

    class Importer
      def initialize(data_dir, repo_id, user_id)
        @data_dir = data_dir
        @user_id = user_id
        @repo_id = repo_id
      end

      def import_with_scope(xmlpath, scope, event, log)
        puts "Importing #{xmlpath}"
        child_path = File.join @data_dir, xmlpath.match(/irn(?<num>\d+)\.xml$/)["num"]

        children = []
        if Dir.exists? child_path
          children = Dir.glob("#{child_path}/irn*xml")
        end

        importer = Importers::IcaAtomEadImporter.new(Graph, scope, log)

        importer.add_creation_callback do |item|
          puts "Created item: #{item.get_id}"
          event.add_subjects item
          log.add_created

          if log.get_successful > 0 and log.get_successful % COMMIT_MAX == 0
            Graph.get_base_graph.commit
          end

          children.each do |cxml|
            import_with_scope(cxml, item, event, log)
          end
        end

        importer.add_update_callback do |item|
          puts "Updated item: #{item.get_id}"
          event.add_subjects item
          log.add_updated

          if log.get_successful > 0 and log.get_successful % COMMIT_MAX == 0
            Graph.get_base_graph.commit
          end

          children.each do |cxml|
            import_with_scope(cxml, item, event, log)
          end
        end

        handler = Importers::IcaAtomEadHandler.new importer
        spf = Java::JavaxXmlParsers::SAXParserFactory.new_instance
        spf.set_namespace_aware false
        spf.set_validating false
        spf.set_schema nil
        parser = spf.new_sax_parser

        File.open(xmlpath, "r") do |f|
          parser.parse(f.to_inputstream, handler)
        end
      end


      def import
        begin
          # lookup USHMM
          ushmm = Manager.get_frame(@repo_id, Models::Repository.java_class)
          user = Manager.get_frame(@user_id, Models::UserProfile.java_class)

          # Start an action!
          ctx = Persistance::ActionManager.new(
            Graph, ushmm).log_event(user, EventTypes::ingest, "Importing USHMM data")
          log = Importers::ImportLog.new(ctx)

          # We basically need recursive behaviour here
          Dir.glob("#{@data_dir}/irn*xml").each do |xmlpath|
            import_with_scope xmlpath, ushmm, ctx, log
          end

          puts "Updated: #{log.get_updated}"
          puts "Created: #{log.get_created}"    

          Graph.get_base_graph.commit
          puts "Committed"
        rescue
          # Oops!
          Graph.get_base_graph.rollback
          raise
        end
      end
    end

    def self.import(data_dir, repo_id, user_id)
      Importer.new(data_dir, repo_id, user_id).import
    end
  end
end
