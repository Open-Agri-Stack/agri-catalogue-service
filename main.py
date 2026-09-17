import os
import re
import json
import argparse
from pathlib import Path
import pyfiglet

class Style:
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    MAGENTA = '\033[95m'
    BLUE = '\033[94m'
    GRAY = '\033[90m'
    BOLD = '\033[1m'
    RESET = '\033[0m'

def rel_path(path: str) -> str:
    try:
        return os.path.relpath(path)
    except Exception:
        return path

# Statistics tracking dictionary
stats = {
    "created": [],
    "updated": [],
    "deleted": [],
    "skipped": []
}

def log_created(file_path: str):
    p = rel_path(file_path)
    stats["created"].append(p)
    print(f"{Style.GREEN}  ✨ Created  {Style.RESET} {p}")

def log_updated(file_path: str):
    p = rel_path(file_path)
    stats["updated"].append(p)
    print(f"{Style.CYAN}  📝 Updated  {Style.RESET} {p}")

def log_deleted(file_path: str):
    p = rel_path(file_path)
    stats["deleted"].append(p)
    print(f"{Style.RED}  🗑️  Deleted  {Style.RESET} {p}")

def log_skipped(message: str):
    stats["skipped"].append(message)
    print(f"{Style.GRAY}  ➖ Skipped  {Style.RESET} {message}")

def log_error(message: str):
    print(f"{Style.RED}{Style.BOLD}  ❌ Error    {Style.RESET} {message}")

def to_pascal_case(name: str) -> str:
    """vergsample -> Vergsample | my-service -> MyService"""
    return ''.join(word.capitalize() for word in re.split(r'[-_\s]', name))

def to_camel_case(name: str) -> str:
    """vergsample -> vergsample | my-service -> myService"""
    pascal = to_pascal_case(name)
    return pascal[0].lower() + pascal[1:]

def to_lower(name: str) -> str:
    """my-service -> myservice"""
    return re.sub(r'[-_\s]', '', name).lower()

def to_upper(name: str) -> str:
    """orderservice -> ORDERSERVICE | my-service -> MY_SERVICE"""
    return re.sub(r'[-\s]', '_', name).upper()

def to_regular(name: str) -> str:
    """vergsample -> Vergsample | my-service -> My Service"""
    return ' '.join(word.capitalize() for word in re.split(r'[-_\s]', name))

def to_bool(value: str) -> bool:
    """Accepts True/False (and common aliases) for argparse flags."""
    if isinstance(value, bool):
        return value
    if value.strip().lower() in ("true", "t", "yes", "y", "1"):
        return True
    if value.strip().lower() in ("false", "f", "no", "n", "0"):
        return False
    raise argparse.ArgumentTypeError(f"Expected True or False, got '{value}'")

class CreateRegistry:

    def __init__(self, service_name: str, skip_schema: bool = False):
        self.skip_schema   = skip_schema
        self.service_name  = service_name
        self.lower         = to_lower(service_name)
        self.pascal        = to_pascal_case(service_name)
        self.camel         = to_camel_case(service_name)
        self.upper         = to_upper(service_name)
        self.regular       = to_regular(service_name)

        # Base paths
        base = os.getcwd()
        self.resource_path  = os.path.join(base, "src", "main", "resources")
        self.registry_path  = os.path.join(base, "src", "main", "java", "com", "catalogue", "verg")
        self.util_path      = os.path.join(base, "src", "main", "java", "com", "catalogue", "verg", "core", "util")

        # Replacements map — used by all template renders
        self.replacements = {
            '{{service_name_lower}}':  self.lower,
            '{{service_name_pascal}}': self.pascal,
            '{{service_name_camel}}':  self.camel,
            '{{service_name_upper}}':  self.upper,
        }

    # ------------------------------------------------------------------ #
    #  Helper                                                              #
    # ------------------------------------------------------------------ #
    def _render_template(self, template_path: str, output_path: str):
        with open(template_path, 'r') as f:
            content = f.read()
        for placeholder, value in self.replacements.items():
            content = content.replace(placeholder, value)
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        with open(output_path, 'w') as f:
            f.write(content)
        log_created(output_path)

    # ------------------------------------------------------------------ #
    #  Java File Generators                                                #
    # ------------------------------------------------------------------ #
    def generate_controller(self):
        self._render_template(
            template_path="registry_template/controller/SampleController.java.template",
            output_path=os.path.join(self.registry_path, self.lower, "controller", f"{self.pascal}Controller.java")
        )

    def generate_entity(self):
        self._render_template(
            template_path="registry_template/entity/SampleEntity.java.template",
            output_path=os.path.join(self.registry_path, self.lower, "entity", f"{self.pascal}Entity.java")
        )

    def generate_repository(self):
        self._render_template(
            template_path="registry_template/repository/SampleRepository.java.template",
            output_path=os.path.join(self.registry_path, self.lower, "repository", f"{self.pascal}Repository.java")
        )

    def generate_service(self):
        self._render_template(
            template_path="registry_template/service/SampleService.java.template",
            output_path=os.path.join(self.registry_path, self.lower, "service", f"{self.pascal}Service.java")
        )

    def generate_service_impl(self):
        self._render_template(
            template_path="registry_template/service/impl/SampleServiceImpl.java.template",
            output_path=os.path.join(self.registry_path, self.lower, "service", "impl", f"{self.pascal}ServiceImpl.java")
        )

    # ------------------------------------------------------------------ #
    #  JSON File Generators                                                #
    # ------------------------------------------------------------------ #
    def generate_payload_validation_json(self):
        output_path = os.path.join(self.resource_path, "payloadValidation", f"{self.camel}PayloadValidation.json")
        if self.skip_schema:
            log_skipped(f"{rel_path(output_path)} (--skipSchema True).")
            return
        if Path(output_path).is_file():
            log_skipped(f"{rel_path(output_path)} already exists.")
            return
        payload_schema = {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "type": "object",
            "properties": {
                f"{self.camel}Id": {
                    "type": "string",
                    "prefix": f"{self.lower}-",
                    "key": "Primary",
                    "keyLength": 12,
                    "description": f"description about {self.camel}Id"
                }
            },
            "required": [f"{self.camel}Id"]
        }
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        with open(output_path, "w") as f:
            json.dump(payload_schema, f, indent=4)
        log_created(output_path)

    def generate_es_mapping_json(self):
        output_path = os.path.join(self.resource_path, "EsFieldsmapping", f"es{self.pascal}RequiredFields.json")
        if self.skip_schema:
            log_skipped(f"{rel_path(output_path)} (--skipSchema True).")
            return
        if Path(output_path).is_file():
            log_skipped(f"{rel_path(output_path)} already exists.")
            return
        es_required_fields = {
            f"{self.camel}Id": {"type": "keyword"},
            "status": {"type": "keyword"},
            "createdOn": {"type": "date"},
            "updatedOn": {"type": "date"},
            "createdBy": {"type": "keyword"},
            "updatedBy": {"type": "keyword"}
        }
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        with open(output_path, "w") as f:
            json.dump(es_required_fields, f, indent=4)
        log_created(output_path)

    # ------------------------------------------------------------------ #
    #  File Editors                                                        #
    # ------------------------------------------------------------------ #
    def append_constants(self):
        constants_path = os.path.join(self.util_path, "Constants.java")
        with open(constants_path, "r") as f:
            content = f.read()
        if f"{self.upper}_VALIDATION_FILE_JSON" in content:
            log_skipped(f"Constants for '{self.camel}' already exist.")
            return
        insertion_point = "    private Constants() {"
        if insertion_point not in content:
            log_error("Could not find insertion point in Constants.java")
            return
        new_constants = f"""
    // {self.pascal} Specific Constants
    public static final String {self.upper}_VALIDATION_FILE_JSON = "/payloadValidation/{self.camel}PayloadValidation.json";
    public static final String {self.upper}_ID_RQST = "{self.camel}Id";
    public static final String {self.upper}_INDEX_NAME = "{self.lower}_index";

    """
        updated_content = content.replace(insertion_point, new_constants + insertion_point)
        with open(constants_path, "w") as f:
            f.write(updated_content)
        log_updated(constants_path)

    def append_application_properties(self):
        properties_path = os.path.join(self.resource_path, "application.properties")
        with open(properties_path, "r") as f:
            content = f.read()
        property_key = f"elastic.required.field.{self.camel}.json.path"
        if property_key in content:
            log_skipped(f"'{property_key}' already exists in application.properties.")
            return
        updated_content = content.rstrip("\n") + f"\n{property_key}=/EsFieldsmapping/es{self.pascal}RequiredFields.json\n"
        with open(properties_path, "w") as f:
            f.write(updated_content)
        log_updated(properties_path)

    def append_verg_properties(self):
        verg_path = os.path.join(self.util_path, "VergProperties.java")
        with open(verg_path, "r") as f:
            content = f.read()
        if f"elastic{self.pascal}JsonPath" in content:
            log_skipped(f"'elastic{self.pascal}JsonPath' already exists in VergProperties.java.")
            return
        new_field = f"""
        @Value("${{elastic.required.field.{self.lower}.json.path}}")
        private String elastic{self.pascal}JsonPath;
    """
        last_brace_index = content.rfind("}")
        updated_content  = content[:last_brace_index] + new_field + content[last_brace_index:]
        with open(verg_path, "w") as f:
            f.write(updated_content)
        log_updated(verg_path)

    # ------------------------------------------------------------------ #
    #  Master Runner                                                       #
    # ------------------------------------------------------------------ #
    def generate_all(self):
        service_dir = os.path.join(self.registry_path, self.lower)
        if not Path(service_dir).is_dir():
            self.generate_controller()
            self.generate_entity()
            self.generate_repository()
            self.generate_service()
            self.generate_service_impl()
        else:
            log_skipped(f"Java files directory '{rel_path(service_dir)}' already exists.")

        self.generate_payload_validation_json()
        self.generate_es_mapping_json()
        self.append_constants()
        self.append_application_properties()
        self.append_verg_properties()


class DeleteRegistry:

    def __init__(self, service_name: str, skip_schema: bool = False):
        self.skip_schema  = skip_schema
        self.service_name = service_name
        self.lower        = to_lower(service_name)
        self.pascal       = to_pascal_case(service_name)
        self.camel        = to_camel_case(service_name)
        self.upper        = to_upper(service_name)
        self.regular       = to_regular(service_name)

        # Base paths (same as CreateRegistry)
        base = os.getcwd()
        self.resource_path = os.path.join(base, "src", "main", "resources")
        self.registry_path = os.path.join(base, "src", "main", "java", "com", "catalogue", "verg")
        self.util_path     = os.path.join(base, "src", "main", "java", "com", "catalogue", "verg", "core", "util")

    # ------------------------------------------------------------------ #
    #  Helper                                                              #
    # ------------------------------------------------------------------ #
    def _delete_file(self, file_path: str):
        if Path(file_path).is_file():
            os.remove(file_path)
            log_deleted(file_path)
        else:
            log_skipped(f"{rel_path(file_path)} does not exist.")

    def _delete_dir(self, dir_path: str):
        if Path(dir_path).is_dir():
            import shutil
            shutil.rmtree(dir_path)
            log_deleted(dir_path)
        else:
            log_skipped(f"{rel_path(dir_path)} does not exist.")

    # ------------------------------------------------------------------ #
    #  Java File Deletors                                                  #
    # ------------------------------------------------------------------ #
    def delete_controller(self):
        self._delete_file(
            os.path.join(self.registry_path, self.lower, "controller", f"{self.pascal}Controller.java")
        )

    def delete_entity(self):
        self._delete_file(
            os.path.join(self.registry_path, self.lower, "entity", f"{self.pascal}Entity.java")
        )

    def delete_repository(self):
        self._delete_file(
            os.path.join(self.registry_path, self.lower, "repository", f"{self.pascal}Repository.java")
        )

    def delete_service(self):
        self._delete_file(
            os.path.join(self.registry_path, self.lower, "service", f"{self.pascal}Service.java")
        )

    def delete_service_impl(self):
        self._delete_file(
            os.path.join(self.registry_path, self.lower, "service", "impl", f"{self.pascal}ServiceImpl.java")
        )

    def delete_service_directory(self):
        # Deletes the entire service folder after all files are removed
        self._delete_dir(
            os.path.join(self.registry_path, self.lower)
        )

    # ------------------------------------------------------------------ #
    #  JSON File Deletors                                                  #
    # ------------------------------------------------------------------ #
    def delete_payload_validation_json(self):
        file_path = os.path.join(self.resource_path, "payloadValidation", f"{self.camel}PayloadValidation.json")
        if self.skip_schema:
            log_skipped(f"{rel_path(file_path)} (--skipSchema True).")
            return
        self._delete_file(file_path)

    def delete_es_mapping_json(self):
        file_path = os.path.join(self.resource_path, "EsFieldsmapping", f"es{self.pascal}RequiredFields.json")
        if self.skip_schema:
            log_skipped(f"{rel_path(file_path)} (--skipSchema True).")
            return
        self._delete_file(file_path)

    # ------------------------------------------------------------------ #
    #  File Editors — Remove injected lines                                #
    # ------------------------------------------------------------------ #
    def remove_constants(self):
        constants_path = os.path.join(self.util_path, "Constants.java")
        with open(constants_path, "r") as f:
            content = f.read()

        if f"{self.upper}_VALIDATION_FILE_JSON" not in content:
            log_skipped(f"Constants for '{self.camel}' do not exist.")
            return

        # Remove the 3 constant lines + the comment line
        pattern = rf"\n\s*// {re.escape(self.pascal)} Specific Constants\n.*?{re.escape(self.upper)}_VALIDATION_FILE_JSON.*?\n.*?{re.escape(self.upper)}_ID_RQST.*?\n.*?{re.escape(self.upper)}_INDEX_NAME.*?\n"
        updated_content = re.sub(pattern, "\n", content)

        with open(constants_path, "w") as f:
            f.write(updated_content)
        log_updated(constants_path)

    def remove_application_properties(self):
        properties_path = os.path.join(self.resource_path, "application.properties")
        with open(properties_path, "r") as f:
            content = f.read()

        property_key = f"elastic.required.field.{self.camel}.json.path"
        if property_key not in content:
            log_skipped(f"Property '{property_key}' does not exist in application.properties.")
            return

        # Remove the line that contains the property key
        updated_lines = [line for line in content.splitlines() if property_key not in line]
        updated_content = "\n".join(updated_lines) + "\n"

        with open(properties_path, "w") as f:
            f.write(updated_content)
        log_updated(properties_path)

    def remove_verg_properties(self):
        verg_path = os.path.join(self.util_path, "VergProperties.java")
        with open(verg_path, "r") as f:
            content = f.read()

        if f"elastic{self.pascal}JsonPath" not in content:
            log_skipped(f"Field 'elastic{self.pascal}JsonPath' does not exist in VergProperties.java.")
            return

        # Remove the @Value annotation line + private field line
        pattern = rf"\n\s*@Value\(\"[^\"]*{re.escape(self.lower)}[^\"]*\"\)\s*\n\s*private String elastic{re.escape(self.pascal)}JsonPath;\s*\n"
        updated_content = re.sub(pattern, "\n", content)

        with open(verg_path, "w") as f:
            f.write(updated_content)
        log_updated(verg_path)

    # ------------------------------------------------------------------ #
    #  Master Runner                                                       #
    # ------------------------------------------------------------------ #
    def delete_all(self):
        # Remove individual files first, then the directory
        self.delete_controller()
        self.delete_entity()
        self.delete_repository()
        self.delete_service()
        self.delete_service_impl()
        self.delete_service_directory()

        # Remove JSON files
        self.delete_payload_validation_json()
        self.delete_es_mapping_json()

        # Undo edits to shared files
        self.remove_constants()
        self.remove_application_properties()
        self.remove_verg_properties()


# ------------------------------------------------------------------ #
#  Entry Point                                                         #
# ------------------------------------------------------------------ #
def print_summary(action, names):
    def print_field(label, value, value_color=""):
        # Total internal width of the box is 70 characters.
        # Margin is 2 spaces on left, 1 space on right.
        # So visible content + padding must equal 67.
        visible_len = len(label) + len(value)
        padding = max(0, 67 - visible_len)
        styled_value = f"{value_color}{value}{Style.RESET}" if value_color else value
        print(f"│  {Style.BOLD}{label}{Style.RESET}{styled_value}{' ' * padding}│")

    print(f"\n{Style.BOLD}┌{'─' * 70}┐{Style.RESET}")
    print(f"{Style.BOLD}│  🚀 Open Agri Stack Execution Report                                 │{Style.RESET}")
    print(f"{Style.BOLD}├{'─' * 70}┤{Style.RESET}")
    print_field("Status:     ", "SUCCESS", Style.GREEN)
    print_field("Action:     ", action.upper())
    print_field("Targets:    ", ", ".join(names))
    print(f"{Style.BOLD}├{'─' * 70}┤{Style.RESET}")
    print(f"│  {Style.GREEN}✨ Created:{Style.RESET} {len(stats['created']):<10} {Style.CYAN}📝 Updated:{Style.RESET} {len(stats['updated']):<10} {Style.RED}🗑️  Deleted:{Style.RESET} {len(stats['deleted']):<10} │")
    print(f"│  {Style.GRAY}➖ Skipped:{Style.RESET} {len(stats['skipped']):<54} │")
    print(f"{Style.BOLD}└{'─' * 70}┘{Style.RESET}\n")

if __name__ == "__main__":
    text = pyfiglet.print_figlet(text="Open Agri Stack", font="slant", colors="green")
    parser = argparse.ArgumentParser()
    parser.add_argument("--name",   required=True, nargs="+",
                        help="Comma-separated catalogue names (e.g. --name cropType,pesticide,fertilizer)")
    parser.add_argument("--action", required=True, choices=["create", "delete"])
    parser.add_argument("--skipSchema", dest="skip_schema", type=to_bool,
                        nargs="?", const=True, default=False,
                        help="True/False. When True, the payloadValidation and EsFieldsmapping "
                             "JSON files are left untouched (not created on --action create, "
                             "not removed on --action delete). Default: False")
    args = parser.parse_args()

    names = [n.replace(" ", "-") for n in re.split(r"[,\s]+", " ".join(args.name)) if n]
    total = len(names)

    for idx, name in enumerate(names, start=1):
        # Premium Box Drawing Header
        header_text = f"[{idx}/{total}] {args.action.upper()} ➔ {name}"
        border_len = len(header_text) + 4
        print(f"\n{Style.BOLD}┌{'─' * border_len}┐{Style.RESET}")
        print(f"{Style.BOLD}│  {header_text}  │{Style.RESET}")
        print(f"{Style.BOLD}└{'─' * border_len}┘\n")

        if args.action == "create":
            CreateRegistry(name, skip_schema=args.skip_schema).generate_all()
        elif args.action == "delete":
            DeleteRegistry(name, skip_schema=args.skip_schema).delete_all()

    print_summary(args.action, names)