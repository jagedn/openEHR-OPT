package com.cabolabs.openehr.opt.parser

import com.cabolabs.openehr.opt.model.*
//import com.thoughtworks.xstream.XStream
import groovy.util.slurpersupport.GPathResult

@groovy.util.logging.Log4j
class OperationalTemplateParser {

   // Parsed XML
   //GPathResult templateXML
   
   // Instance to be generated by parsing the XML
   OperationalTemplate template

   /**
    * Parses the XML contents of a template. This class doesn't know where
    * the template is located, e.g. filesystem, web service, etc.
    */
   OperationalTemplate parse(String templateContents)
   {
      def templateXML = new XmlSlurper().parseText( templateContents ) // GPathResult
      
      // TODO: validate against XSD
      
      return parseOperationalTemplate(templateXML)
   }
   
   private parseOperationalTemplate(GPathResult tpXML)
   {
      this.template = new OperationalTemplate(
         uid: tpXML.uid.value.text(),
         templateId: tpXML.template_id.value.text(),
         concept: tpXML.concept.text(),
         language: parseCodePhrase(tpXML.language),
         isControlled: ((!tpXML.is_controlled.isEmpty() && tpXML.is_controlled.text() != '') ? tpXML.is_controlled.text() : false),
         purpose: tpXML.description.details.purpose.text(),
         // TODO: add use, misuse, keywords, etc. from RESOURCE_DESCRIPTION_ITEM: https://github.com/openEHR/java-libs/blob/f6ee434226bf926d261c2690016c1d6022b877be/oet-parser/src/main/xsd/Resource.xsd
      )
      
      this.template.definition = parseObjectNode(tpXML.definition, '/', '/')
      
      
      // DEBUG
      /*
      def xstream = new XStream()
      def xml = xstream.toXML(this.template)
      def random = new Random()
      def randomInt = random.nextInt(20000)
      new File("template_"+ randomInt +".xml").write( xml )
      */
      // /DEBUG
      
      return this.template
   }
   
   private parseCodePhrase(GPathResult node)
   {
      return node.terminology_id.value.text() +"::"+ node.code_string.text()
   }
   
   private parseObjectNode(GPathResult node, String parentPath, String path)
   {
      // Path calculation
      def templatePath = parentPath
      
      if (templatePath != '/')
      {
         // comienza de nuevo con las paths relativas al root de este arquetipo
         if (!node.archetype_id.value.isEmpty())
         {
            templatePath += '[archetype_id='+ node.archetype_id.value +']' // slot in the path instead of node_id
            
            if (node.'@xsi:type'.text() == "C_ARCHETYPE_ROOT")
            {
               path = '/' // archetype root found
            }
         }
         // para tag vacia empty da false pero text es vacio ej. <node_id/>
         else if (!node.node_id.isEmpty() && node.node_id.text() != '')
         {
            templatePath += '['+ node.node_id.text() + ']'
            path += '['+ node.node_id.text() + ']'
         }
      }
      
      def terminologyRef
      if (node.rm_type_name.text() == 'CODE_PHRASE')
      {
         def uri = node.referenceSetUri.text()
         if (uri) terminologyRef = uri
      }
      
      //println "path: "+ path
      
      def obn = new ObjectNode(
         rmTypeName: node.rm_type_name.text(),
         nodeId: node.node_id.text(),
         type: node.'@xsi:type'.text(),
         archetypeId: node.archetype_id.value.text(), // This is optional, just resolved slots have archId
         templatePath: templatePath,
         path: path,
         xmlNode: node, // Quick fix until having each constraint type modeled
         terminologyRef: terminologyRef
         // TODO: default_values
      )
      
      // TODO: parse occurrences
      
      node.attributes.each { xatn ->
      
         obn.attributes << parseAttributeNode(xatn, templatePath, path)
      }
      
      node.term_definitions.each { tdef ->
         
         obn.termDefinitions << parseCodedTerm(tdef)
      }
      
      // Traverse all subnodes to get the flat structure for path->node
      this.setFlatNodes(obn)
      
      // Used by guigen and binder
      this.template.nodes[templatePath] = obn
      
      return obn
   }
   
   private setFlatNodes(ObjectNode parent)
   {
      this.template.nodes.each { path, obn ->
         if (obn.path.startsWith(parent.path))
            parent.nodes[obn.path] = obn // uses archetype paths not template paths!
      }
   }
   
   private parseAttributeNode(GPathResult attr, String parentPath, String path)
   {
      // Path calculation
      def templatePath = parentPath
      if (templatePath == '/') templatePath += attr.rm_attribute_name.text() // Avoids to repeat '/'
      else templatePath += '/'+ attr.rm_attribute_name.text()
      
      def nextArchPath
      if (path == '/') nextArchPath = path + attr.rm_attribute_name.text()
      else nextArchPath = path +'/'+ attr.rm_attribute_name.text()
      
      def atn = new AttributeNode(
         rmAttributeName: attr.rm_attribute_name.text(),
         type: attr.'@xsi:type'.text()
         // TODO: cardinality
         // TODO: existence
      )
      
      attr.children.each { xobn ->
      
         atn.children << parseObjectNode(xobn, templatePath, nextArchPath)
      }
      
      return atn
   }
   
   private parseCodedTerm(GPathResult node)
   {
      return new CodedTerm(
         code: node.@code.text(),
         term: parseTerm(node.items))
   }
   
   private parseTerm(GPathResult node)
   {
      return new Term(
         text: node.find{ it.@id == 'text' }.text(),
         description: node.find{ it.@id == 'description' }.text())
   }
}
