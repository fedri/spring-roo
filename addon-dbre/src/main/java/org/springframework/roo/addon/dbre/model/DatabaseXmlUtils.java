package org.springframework.roo.addon.dbre.model;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.springframework.roo.support.util.StringUtils;
import org.springframework.roo.support.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Assists converting a {@link Database} to and from XML using DOM or SAX.
 * 
 * @author Alan Stewart
 * @since 1.1
 */
public abstract class DatabaseXmlUtils {
	static final String NAME = "name";
	static final String LOCAL = "local";
	static final String FOREIGN = "foreign";
	static final String FOREIGN_TABLE = "foreignTable";
	static final String DESCRIPTION = "description";
	static final String REFERENCE = "reference";
	static final String SEQUENCE_NUMBER = "sequenceNumber";
	static final String ON_UPDATE = "onUpdate";
	static final String ON_DELETE = "onDelete";

	static enum IndexType {
		INDEX, UNIQUE
	}

	public static void writeDatabaseStructureToOutputStream(Database database, OutputStream outputStream) {
		Document document = XmlUtils.getDocumentBuilder().newDocument();

		Element databaseElement = document.createElement("database");
		databaseElement.setAttribute(NAME, database.getName());
		if (database.getSchema() != null) {
			databaseElement.setAttribute("schema", database.getSchema().getName());
		}

		for (Table table : database.getTables()) {
			Element tableElement = document.createElement("table");
			tableElement.setAttribute(NAME, table.getName());
			if (StringUtils.hasText(table.getDescription())) {
				tableElement.setAttribute(DESCRIPTION, table.getDescription());
			}

			for (Column column : table.getColumns()) {
				Element columnElement = document.createElement("column");
				columnElement.setAttribute(NAME, column.getName());
				if (StringUtils.hasText(column.getDescription())) {
					columnElement.setAttribute(DESCRIPTION, column.getDescription());
				}
				columnElement.setAttribute("primaryKey", String.valueOf(column.isPrimaryKey()));
				columnElement.setAttribute("required", String.valueOf(column.isRequired()));
				
				if (column.getLength() > 0) {
					columnElement.setAttribute("size", String.valueOf(column.getLength()));
				} else {
					columnElement.setAttribute("size", String.valueOf(column.getPrecision() + "," + String.valueOf(column.getScale())));
				}
				
				columnElement.setAttribute("type", column.getType().name());
				columnElement.setAttribute("index", String.valueOf(column.getOrdinalPosition()));
				tableElement.appendChild(columnElement);
			}

			for (ForeignKey foreignKey : table.getForeignKeys()) {
				Element foreignKeyElement = document.createElement("foreignKey");
				String foreignTableName = foreignKey.getForeignTableName();
				foreignKeyElement.setAttribute(NAME, foreignKey.getName());
				foreignKeyElement.setAttribute(FOREIGN_TABLE, foreignTableName);
				foreignKeyElement.setAttribute(ON_DELETE, foreignKey.getOnDelete().getCode());
				foreignKeyElement.setAttribute(ON_UPDATE, foreignKey.getOnUpdate().getCode());

				for (org.springframework.roo.addon.dbre.model.Reference reference : foreignKey.getReferences()) {
					Element referenceElement = document.createElement(REFERENCE);
					referenceElement.setAttribute(FOREIGN, reference.getForeignColumnName());
					referenceElement.setAttribute(LOCAL, reference.getLocalColumnName());
					referenceElement.setAttribute(SEQUENCE_NUMBER, reference.getSequenceNumber().toString());
					foreignKeyElement.appendChild(referenceElement);
				}
				tableElement.appendChild(foreignKeyElement);
			}

			for (Index index : table.getIndices()) {
				Element indexElement = document.createElement(index.isUnique() ? IndexType.UNIQUE.name().toLowerCase() : IndexType.INDEX.name().toLowerCase());
				indexElement.setAttribute(NAME, index.getName());
				for (IndexColumn indexColumn : index.getColumns()) {
					Element indexColumnElement = document.createElement((index.isUnique() ? IndexType.UNIQUE.name().toLowerCase() : IndexType.INDEX.name().toLowerCase()) + "-column");
					indexColumnElement.setAttribute(NAME, indexColumn.getName());
					indexColumnElement.setAttribute(SEQUENCE_NUMBER, String.valueOf(indexColumn.getOrdinalPosition()));
					indexElement.appendChild(indexColumnElement);
				}
				tableElement.appendChild(indexElement);
			}

			for (ForeignKey exportedKey : table.getExportedKeys()) {
				Element exportedKeyElement = document.createElement("exportedKey");
				String foreignTableName = exportedKey.getForeignTableName();
				exportedKeyElement.setAttribute(NAME, exportedKey.getName());
				exportedKeyElement.setAttribute(FOREIGN_TABLE, foreignTableName);
				exportedKeyElement.setAttribute(ON_DELETE, exportedKey.getOnDelete().getCode());
				exportedKeyElement.setAttribute(ON_UPDATE, exportedKey.getOnUpdate().getCode());

				for (org.springframework.roo.addon.dbre.model.Reference reference : exportedKey.getReferences()) {
					Element referenceElement = document.createElement(REFERENCE);
					referenceElement.setAttribute(FOREIGN, reference.getForeignColumnName());
					referenceElement.setAttribute(LOCAL, reference.getLocalColumnName());
					referenceElement.setAttribute(SEQUENCE_NUMBER, reference.getSequenceNumber().toString());
					exportedKeyElement.appendChild(referenceElement);
				}
				tableElement.appendChild(exportedKeyElement);
			}

			databaseElement.appendChild(tableElement);
		}

		Set<Sequence> sequences = database.getSequences();
		if (!sequences.isEmpty()) {
			Element sequencesElement = document.createElement("sequences");
			for (Sequence sequence : sequences) {
				Element sequenceElement = document.createElement("sequence");
				sequenceElement.setAttribute(NAME, sequence.getName());
				sequencesElement.appendChild(sequenceElement);
			}
			databaseElement.appendChild(sequencesElement);
		}

		document.appendChild(databaseElement);

		XmlUtils.writeXml(outputStream, document);
	}

	public static Schema readSchemaFromInputStream(InputStream inputStream) {
		Document document = getDocument(inputStream);
		Element databaseElement = document.getDocumentElement();
		return new Schema(databaseElement.getAttribute("schema"));
	}

	public static Schema readSchemaUsingSaxFromInputStream(InputStream inputStream) {
		try {
			SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser parser = spf.newSAXParser();
			SchemaContentHandler contentHandler = new SchemaContentHandler();
			parser.parse(inputStream, contentHandler);
			return contentHandler.getSchema();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static Database readDatabaseStructureFromInputStream(InputStream inputStream) {
		Document document = getDocument(inputStream);
		Element databaseElement = document.getDocumentElement();

		Set<Table> tables = new LinkedHashSet<Table>();
		List<Element> tableElements = XmlUtils.findElements("table", databaseElement);
		for (Element tableElement : tableElements) {
			Table table = new Table();
			table.setName(tableElement.getAttribute(NAME));
			if (StringUtils.hasText(tableElement.getAttribute(DESCRIPTION))) {
				table.setDescription(tableElement.getAttribute(DESCRIPTION));
			}

			List<Element> columnElements = XmlUtils.findElements("column", tableElement);
			for (Element columnElement : columnElements) {
				String name = columnElement.getAttribute(NAME);
				Column column = new Column(name);
				column.setDescription(columnElement.getAttribute(DESCRIPTION));
				column.setPrimaryKey(Boolean.parseBoolean(columnElement.getAttribute("primaryKey")));
				column.setJavaType(columnElement.getAttribute("javaType"));
				column.setRequired(Boolean.parseBoolean(columnElement.getAttribute("required")));
				
				String size = columnElement.getAttribute("size");
				if (size.contains(",")) {
					String[] precisionScale = StringUtils.split(size, ",");
					column.setPrecision(Integer.parseInt(precisionScale[0]));
					column.setScale(Integer.parseInt(precisionScale[1]));
				} else {
					column.setLength(Integer.parseInt(size));
				}
				
				column.setType(ColumnType.valueOf(columnElement.getAttribute("type")));
				column.setOrdinalPosition(Integer.parseInt(columnElement.getAttribute("index")));
				table.addColumn(column);
			}

			List<Element> foreignKeyElements = XmlUtils.findElements("foreignKey", tableElement);
			for (Element foreignKeyElement : foreignKeyElements) {
				ForeignKey foreignKey = new ForeignKey(foreignKeyElement.getAttribute(NAME), foreignKeyElement.getAttribute(FOREIGN_TABLE));
				foreignKey.setOnDelete(CascadeAction.getCascadeAction(foreignKeyElement.getAttribute(ON_DELETE)));
				foreignKey.setOnUpdate(CascadeAction.getCascadeAction(foreignKeyElement.getAttribute(ON_UPDATE)));

				List<Element> referenceElements = XmlUtils.findElements(REFERENCE, foreignKeyElement);
				for (Element referenceElement : referenceElements) {
					org.springframework.roo.addon.dbre.model.Reference reference = new org.springframework.roo.addon.dbre.model.Reference();
					reference.setForeignColumnName(referenceElement.getAttribute(FOREIGN));
					reference.setLocalColumnName(referenceElement.getAttribute(LOCAL));
					reference.setSequenceNumber(new Short(referenceElement.getAttribute(SEQUENCE_NUMBER)));
					foreignKey.addReference(reference);
				}
				table.addForeignKey(foreignKey);
			}

			List<Element> exportedKeyElements = XmlUtils.findElements("exportedKey", tableElement);
			for (Element exportedKeyElement : exportedKeyElements) {
				ForeignKey exportedKey = new ForeignKey(exportedKeyElement.getAttribute(NAME), exportedKeyElement.getAttribute(FOREIGN_TABLE));
				exportedKey.setOnDelete(CascadeAction.getCascadeAction(exportedKeyElement.getAttribute(ON_DELETE)));
				exportedKey.setOnUpdate(CascadeAction.getCascadeAction(exportedKeyElement.getAttribute(ON_UPDATE)));

				List<Element> referenceElements = XmlUtils.findElements(REFERENCE, exportedKeyElement);
				for (Element referenceElement : referenceElements) {
					org.springframework.roo.addon.dbre.model.Reference reference = new org.springframework.roo.addon.dbre.model.Reference();
					reference.setForeignColumnName(referenceElement.getAttribute(FOREIGN));
					reference.setLocalColumnName(referenceElement.getAttribute(LOCAL));
					reference.setSequenceNumber(new Short(referenceElement.getAttribute(SEQUENCE_NUMBER)));
					exportedKey.addReference(reference);
				}
				table.addExportedKey(exportedKey);
			}

			addIndices(table, tableElement, IndexType.INDEX);
			addIndices(table, tableElement, IndexType.UNIQUE);

			tables.add(table);
		}

		Set<Sequence> sequences = new LinkedHashSet<Sequence>();
		List<Element> sequenceElements = XmlUtils.findElements("sequences/sequence", databaseElement);
		for (Element sequenceElement : sequenceElements) {
			Sequence sequence = new Sequence(sequenceElement.getAttribute(NAME));
			sequences.add(sequence);
		}

		String name = databaseElement.getAttribute(NAME);
		Schema schema = new Schema(databaseElement.getAttribute("schema"));

		Database database = new Database(name, schema, tables);
		database.setSequences(sequences);
		return database;
	}

	private static void addIndices(Table table, Element tableElement, IndexType indexType) {
		List<Element> elements = XmlUtils.findElements(indexType.name().toLowerCase(), tableElement);
		for (Element element : elements) {
			Index index = new Index(element.getAttribute(NAME));
			index.setUnique(indexType == IndexType.UNIQUE);
			List<Element> indexColumnElements = XmlUtils.findElements(indexType.name().toLowerCase() + "-column", element);
			for (Element indexColumnElement : indexColumnElements) {
				IndexColumn indexColumn = new IndexColumn(indexColumnElement.getAttribute(NAME));
				indexColumn.setOrdinalPosition(new Short(indexColumnElement.getAttribute(SEQUENCE_NUMBER)));
				index.addColumn(indexColumn);
			}
			table.addIndex(index);
		}
	}

	private static Document getDocument(InputStream inputStream) {
		try {
			DocumentBuilder builder = XmlUtils.getDocumentBuilder();
			builder.setErrorHandler(null);
			return builder.parse(inputStream);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static Database readDatabaseStructureUsingSaxFromInputStream(InputStream inputStream) {
		try {
			SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser parser = spf.newSAXParser();
			DatabaseContentHandler contentHandler = new DatabaseContentHandler();
			parser.parse(inputStream, contentHandler);
			return contentHandler.getDatabase();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
