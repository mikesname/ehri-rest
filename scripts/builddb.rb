#!/usr/bin/env ruby


$:.unshift File.join(File.dirname(__FILE__), "ruby", "lib")

require "ehriutils"
require "fileutils"

USER_ID = ARGV.shift || ENV["USER"]
EAG_DATA_DIR = "eag"
USHMM_DATA_DIR = "ead"
EAD_DATA_DIR = "icaatom-export"
USHMM_ID = "us-005578"
USER_FIXTURES = "#{ENV["HOME"]}/Dropbox/EHRI/users.yaml"

# Check stuff works
EhriUtils::check_env

if Dir.exist?(ENV["NEO4J_DB"])
    # Check if the graph DB exists
    OPTS = {
        1 => "Yes",
        2 => "No",
        3 => "Backup",
        4 => "Delete"
    }

    puts "Neo4j database \"#{ENV["NEO4J_DB"]}\" already exists. Continue?"
    OPTS.each { |opt, text|
        puts "#{opt}) #{text}"
    }
    while true
        print "> "
        choice = gets.chomp.to_i
        if OPTS.keys.include?(choice)
            case choice
            when 2
                exit
            when 3
                FileUtils.mv(ENV["NEO4J_DB"], "#{ENV["NEO4J_DB"]}_#{Time.now.strftime("%Y%m%d%H%M%S")}")
            when 4
                FileUtils.rm_r(ENV["NEO4J_DB"])
            end
            break
        end
    end
end

require "ehri"
require "ushmmimporter"
require "icaatomimporter"
require "eagimporter"

# Load the environment. Order of execution is important
# here because we can't delete/backup the graph once it
# is loaded.
include Ehri

puts "Initializing..."
Commands::Initialize.new.exec(Graph, [].to_java(:string))

puts "Importing users from YAML..."
Commands::LoadFixtures.new.exec(Graph, [USER_FIXTURES].to_java(:string))

puts "Importing repository EAG..."
EagImporter::import(EAG_DATA_DIR, USER_ID)

puts "Importing ICA-Atom EAD..."
IcaAtomImporter::import(EAD_DATA_DIR, USER_ID)

puts "Importing USHMM EAD data..."
UshmmImporter::import(USHMM_DATA_DIR, USHMM_ID, USER_ID)



